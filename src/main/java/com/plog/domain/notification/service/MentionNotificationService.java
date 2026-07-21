package com.plog.domain.notification.service;

import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.event.ChatMentionEvent;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.infrastructure.fcm.FcmDeliveryException;
import com.plog.infrastructure.fcm.FcmGateway;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentionNotificationService {
    private static final int MAX_RETRIES = 3;
    private static final int MAX_PREVIEW_LENGTH = 100;

    private final ProjectMemberRepository projectMemberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final FcmGateway fcmGateway;

    public void send(ChatMentionEvent event) {
        if (!isValid(event)) {
            log.warn("fcm_mention_event_rejected projectId={} chatId={} senderMemberId={}",
                    event == null ? null : event.projectId(),
                    event == null ? null : event.chatId(),
                    event == null ? null : event.senderMemberId());
            return;
        }

        Set<Long> requestedMemberIds = event.mentionMemberIds().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        requestedMemberIds.add(event.senderMemberId());
        Map<Long, ProjectMember> membersById = projectMemberRepository.findAllByIdIn(requestedMemberIds).stream()
                .collect(Collectors.toMap(ProjectMember::getId, Function.identity()));

        ProjectMember sender = membersById.get(event.senderMemberId());
        if (!isActiveProjectMember(sender, event.projectId())) {
            log.warn("fcm_mention_sender_rejected projectId={} chatId={} senderMemberId={}",
                    event.projectId(), event.chatId(), event.senderMemberId());
            return;
        }

        List<ProjectMember> targets = event.mentionMemberIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .filter(memberId -> !memberId.equals(event.senderMemberId()))
                .map(membersById::get)
                .filter(member -> isActiveProjectMember(member, event.projectId()))
                .toList();
        Set<Long> targetUserIds = targets.stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (targetUserIds.isEmpty()) {
            return;
        }

        String senderNickname = sender.getAnNickname() == null || sender.getAnNickname().isBlank()
                ? "프로젝트 멤버" : sender.getAnNickname();
        String title = sender.getProject().getProjectName() + " 멘션";
        String preview = preview(event.messagePreview());
        String body = preview.isEmpty()
                ? senderNickname + "님이 회원님을 멘션했습니다."
                : senderNickname + "님: " + preview;
        Map<String, String> data = Map.of(
                "projectId", event.projectId().toString(),
                "chatId", event.chatId().toString(),
                "type", "CHAT_MENTION"
        );

        List<FcmToken> tokens = new ArrayList<>(fcmTokenRepository.findAllByUserIdIn(targetUserIds));
        for (FcmToken token : tokens) {
            sendWithRetry(token.getToken(), title, body, data);
        }
    }

    private void sendWithRetry(String token, String title, String body, Map<String, String> data) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                fcmGateway.send(new FcmMessage(token, title, body, data));
                return;
            } catch (FcmDeliveryException exception) {
                if (exception.isInvalidToken()) {
                    deleteInvalidToken(token);
                    return;
                }
                if (attempt > MAX_RETRIES) {
                    log.error("fcm_mention_delivery_failed tokenLength={} attempts={}",
                            token.length(), attempt, exception);
                    return;
                }
                backoff(attempt);
            } catch (RuntimeException exception) {
                if (attempt > MAX_RETRIES) {
                    log.error("fcm_mention_delivery_failed tokenLength={} attempts={}",
                            token.length(), attempt, exception);
                    return;
                }
                backoff(attempt);
            }
        }
    }

    private void deleteInvalidToken(String token) {
        fcmTokenRepository.deleteByToken(token);
    }

    private boolean isValid(ChatMentionEvent event) {
        return event != null && event.projectId() != null && event.chatId() != null
                && event.senderMemberId() != null && event.mentionMemberIds() != null;
    }

    private boolean isActiveProjectMember(ProjectMember member, Long projectId) {
        return member != null && member.getStatus() == MemberStatus.ACTIVE
                && member.getProject() != null && projectId.equals(member.getProject().getId());
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_PREVIEW_LENGTH
                ? normalized : normalized.substring(0, MAX_PREVIEW_LENGTH) + "…";
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(200L * (1L << (attempt - 1)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FCM 재시도 대기가 중단되었습니다.", exception);
        }
    }
}
