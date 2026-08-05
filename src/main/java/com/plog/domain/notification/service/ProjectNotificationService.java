package com.plog.domain.notification.service;

import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.event.ChatMessageNotificationEvent;
import com.plog.domain.notification.event.PeerEvaluationStartedEvent;
import com.plog.domain.notification.event.ReportPublishedEvent;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.notification.repository.NotificationRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.infrastructure.fcm.FcmDeliveryException;
import com.plog.infrastructure.fcm.FcmGateway;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectNotificationService {
    private static final int MAX_RETRIES = 3;

    private final ProjectMemberRepository projectMemberRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final FcmGateway fcmGateway;

    @Transactional
    public void sendChatMessage(ChatMessageNotificationEvent event) {
        if (event == null || event.projectId() == null || event.roomId() == null || event.chatId() == null
                || event.senderMemberId() == null) {
            return;
        }
        List<ProjectMember> activeMembers = activeMembers(event.projectId());
        ProjectMember sender = activeMembers.stream()
                .filter(member -> event.senderMemberId().equals(member.getId()))
                .findFirst().orElse(null);
        if (sender == null) {
            return;
        }
        Set<Long> excluded = new LinkedHashSet<>(event.mentionMemberIds());
        excluded.add(event.senderMemberId());
        List<ProjectMember> targets = activeMembers.stream()
                .filter(member -> !excluded.contains(member.getId()))
                .toList();
        String body = senderName(sender) + "님이 새 메시지를 보냈습니다.";
        deliver(targets, sender.getProject(), NotificationType.CHAT_MESSAGE, body, event.chatId(), Map.of(
                "projectId", event.projectId().toString(),
                "roomId", event.roomId().toString(),
                "resourceId", event.chatId().toString(),
                "type", NotificationType.CHAT_MESSAGE.name()));
    }

    @Transactional
    public void sendPeerEvaluationStarted(PeerEvaluationStartedEvent event) {
        if (event == null || event.projectId() == null) {
            return;
        }
        if (notificationRepository.existsByProjectIdAndType(
                event.projectId(), NotificationType.PEER_EVALUATION_STARTED)) {
            return;
        }
        List<ProjectMember> targets = activeMembers(event.projectId()).stream()
                .filter(member -> event.initiatorMemberId() == null
                        || !event.initiatorMemberId().equals(member.getId()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        Project project = targets.get(0).getProject();
        deliver(targets, project, NotificationType.PEER_EVALUATION_STARTED,
                project.getProjectName() + "의 피어 평가가 시작되었습니다.", null, Map.of(
                        "projectId", event.projectId().toString(),
                        "type", NotificationType.PEER_EVALUATION_STARTED.name()));
    }

    @Transactional
    public void sendReportPublished(ReportPublishedEvent event) {
        if (event == null || event.projectId() == null || event.reportId() == null) {
            return;
        }
        if (notificationRepository.existsByProjectIdAndTypeAndResourceId(
                event.projectId(), NotificationType.REPORT_PUBLISHED, event.reportId())) {
            return;
        }
        List<ProjectMember> targets = activeMembers(event.projectId());
        if (targets.isEmpty()) {
            return;
        }
        Project project = targets.get(0).getProject();
        deliver(targets, project, NotificationType.REPORT_PUBLISHED,
                project.getProjectName() + "의 리포트가 발행되었습니다.", event.reportId(), Map.of(
                        "projectId", event.projectId().toString(),
                        "resourceId", event.reportId().toString(),
                        "type", NotificationType.REPORT_PUBLISHED.name()));
    }

    private List<ProjectMember> activeMembers(Long projectId) {
        return projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(projectId, MemberStatus.ACTIVE);
    }

    private void deliver(
            List<ProjectMember> targets,
            Project project,
            NotificationType type,
            String body,
            Long resourceId,
            Map<String, String> data
    ) {
        if (targets.isEmpty()) {
            return;
        }
        notificationRepository.saveAll(targets.stream()
                .map(member -> Notification.create(member.getUser(), project, type, body, resourceId))
                .toList());
        Set<Long> userIds = targets.stream().map(member -> member.getUser().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (FcmToken token : fcmTokenRepository.findAllByUserIdIn(userIds)) {
            sendWithRetry(token.getToken(), project.getProjectName(), body, data, type);
        }
    }

    private void sendWithRetry(
            String token,
            String title,
            String body,
            Map<String, String> data,
            NotificationType type
    ) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                fcmGateway.send(new FcmMessage(token, title, body, data));
                return;
            } catch (FcmDeliveryException exception) {
                if (exception.isInvalidToken()) {
                    fcmTokenRepository.deleteByToken(token);
                    return;
                }
                if (attempt > MAX_RETRIES) {
                    log.error("project_notification_delivery_failed type={} attempts={}", type, attempt, exception);
                    return;
                }
                backoff(attempt);
            } catch (RuntimeException exception) {
                if (attempt > MAX_RETRIES) {
                    log.error("project_notification_delivery_failed type={} attempts={}", type, attempt, exception);
                    return;
                }
                backoff(attempt);
            }
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(200L * (1L << (attempt - 1)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FCM 재시도 대기가 중단되었습니다.", exception);
        }
    }

    private String senderName(ProjectMember sender) {
        if (sender.getAnNickname() != null && !sender.getAnNickname().isBlank()) {
            return sender.getAnNickname();
        }
        String nickname = sender.getUser().getNickname();
        return nickname == null || nickname.isBlank() ? "프로젝트 멤버" : nickname;
    }
}
