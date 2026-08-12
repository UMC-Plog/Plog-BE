package com.plog.domain.notification.service;

import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.event.ChatMentionEvent;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.notification.repository.NotificationRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.util.AfterCommitExecutor;
import com.plog.infrastructure.fcm.FcmDeliveryException;
import com.plog.infrastructure.fcm.FcmGateway;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentionNotificationService {
    private static final int MAX_RETRIES = 3;

    private final ProjectMemberRepository projectMemberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final FcmGateway fcmGateway;
    private final NotificationPushPolicy notificationPushPolicy;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional
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
        if (targets.isEmpty()) {
            return;
        }

        Project project = sender.getProject();
        String senderNickname = resolveNickname(sender);
        String title = project.getProjectName();
        String body = senderNickname + "님이 회원님을 멘션했습니다.";
        Map<String, String> data = Map.of(
                "projectId", event.projectId().toString(),
                "roomId", event.roomId().toString(),
                "resourceId", event.chatId().toString(),
                "type", "CHAT_MENTION"
        );

        // 인앱 알림 이력은 FCM 발송 성공 여부와 무관하게 항상 남긴다(알림 센터는 별개 채널이므로).
        List<Notification> notifications = targets.stream()
                .map(target -> Notification.create(
                        target.getUser(), project, NotificationType.CHAT_MENTION, body, event.chatId()))
                .toList();
        notificationRepository.saveAll(notifications);

        Set<Long> targetUserIds = targets.stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> pushUserIds = targetUserIds.stream()
                .filter(userId -> notificationPushPolicy.isEnabled(
                        userId, event.projectId(), NotificationType.CHAT_MENTION))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> tokens = pushUserIds.isEmpty()
                ? List.of()
                : fcmTokenRepository.findAllByUserIdIn(pushUserIds).stream()
                        .map(FcmToken::getToken)
                        .toList();
        tokens.forEach(token -> afterCommitExecutor.execute(() ->
                sendWithRetry(token, title, body, data, event.projectId())));
    }

    // 표시 닉네임 정책: anNickname 우선, 없으면 user.nickname으로 대체.
    // 멘션 매칭 조회(ProjectMemberRepository)와 동일한 기준을 써야
    // "매칭에 쓰인 이름"과 "알림에 보이는 이름"이 어긋나지 않는다.
    private String resolveNickname(ProjectMember member) {
        if (member.getAnNickname() != null && !member.getAnNickname().isBlank()) {
            return member.getAnNickname();
        }
        String userNickname = member.getUser().getNickname();
        return userNickname == null || userNickname.isBlank() ? "프로젝트 멤버" : userNickname;
    }

    private void sendWithRetry(String token, String title, String body, Map<String, String> data, Long projectId) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                fcmGateway.send(new FcmMessage(token, title, body, data));
                log.info("project_notification_delivery_succeeded type={} projectId={}",
                        NotificationType.CHAT_MENTION, projectId);
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
        return event != null && event.projectId() != null && event.roomId() != null && event.chatId() != null
                && event.senderMemberId() != null && event.mentionMemberIds() != null;
    }

    private boolean isActiveProjectMember(ProjectMember member, Long projectId) {
        return member != null && member.getStatus() == MemberStatus.ACTIVE
                && member.getProject() != null && projectId.equals(member.getProject().getId());
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
