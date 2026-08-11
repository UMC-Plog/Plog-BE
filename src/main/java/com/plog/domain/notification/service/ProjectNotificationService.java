package com.plog.domain.notification.service;

import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.event.ChatMessageNotificationEvent;
import com.plog.domain.notification.event.IntegrationCollectionCompletedEvent;
import com.plog.domain.notification.event.NoticePublishedEvent;
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
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final NotificationPushPolicy notificationPushPolicy;

    @Transactional
    public void sendChatMessage(ChatMessageNotificationEvent event) {
        if (event == null || event.projectId() == null || event.roomId() == null || event.chatId() == null
                || event.senderMemberId() == null) {
            log.warn("chat_message_notification_rejected projectId={} roomId={} chatId={} senderMemberId={}",
                    event == null ? null : event.projectId(),
                    event == null ? null : event.roomId(),
                    event == null ? null : event.chatId(),
                    event == null ? null : event.senderMemberId());
            return;
        }
        Set<Long> requestedMemberIds = new LinkedHashSet<>(event.targetMemberIds());
        requestedMemberIds.add(event.senderMemberId());
        Map<Long, ProjectMember> membersById = projectMemberRepository.findAllByIdIn(requestedMemberIds).stream()
                .collect(Collectors.toMap(ProjectMember::getId, Function.identity()));
        ProjectMember sender = membersById.get(event.senderMemberId());
        if (!isActiveProjectMember(sender, event.projectId())) {
            log.warn("chat_message_notification_sender_rejected projectId={} roomId={} chatId={} senderMemberId={}",
                    event.projectId(), event.roomId(), event.chatId(), event.senderMemberId());
            return;
        }
        List<ProjectMember> targets = event.targetMemberIds().stream()
                .distinct()
                .map(membersById::get)
                .filter(member -> isActiveProjectMember(member, event.projectId()))
                .toList();
        log.info("chat_message_notification_targets projectId={} roomId={} chatId={} targetCount={}",
                event.projectId(), event.roomId(), event.chatId(), targets.size());
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
        notificationRepository.acquireDedupeLock(
                event.projectId() + ":" + NotificationType.PEER_EVALUATION_STARTED.name());
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
    public void sendNoticePublished(NoticePublishedEvent event) {
        if (event == null || event.projectId() == null || event.postId() == null) {
            return;
        }
        notificationRepository.acquireDedupeLock(event.projectId() + ":"
                + NotificationType.NOTICE.name() + ":" + event.postId());
        if (notificationRepository.existsByProjectIdAndTypeAndResourceId(
                event.projectId(), NotificationType.NOTICE, event.postId())) {
            return;
        }
        List<ProjectMember> targets = activeMembers(event.projectId());
        if (targets.isEmpty()) {
            return;
        }
        Project project = targets.get(0).getProject();
        deliver(targets, project, NotificationType.NOTICE,
                "새 공지가 등록되었습니다.", event.postId(), Map.of(
                        "projectId", event.projectId().toString(),
                        "resourceId", event.postId().toString(),
                        "type", NotificationType.NOTICE.name()));
    }

    @Transactional
    public void sendReportPublished(ReportPublishedEvent event) {
        if (event == null || event.projectId() == null || event.reportId() == null) {
            return;
        }
        notificationRepository.acquireDedupeLock(event.projectId() + ":"
                + NotificationType.REPORT_PUBLISHED.name() + ":" + event.reportId());
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

    @Transactional
    public void sendIntegrationCollectionCompleted(IntegrationCollectionCompletedEvent event) {
        if (event == null || event.projectId() == null || event.collectionJobId() == null
                || event.requestedByProjectMemberId() == null) {
            return;
        }
        NotificationType type = NotificationType.INTEGRATION_COLLECTION_COMPLETED;
        notificationRepository.acquireDedupeLock(
                event.projectId() + ":" + type.name() + ":" + event.collectionJobId());
        if (notificationRepository.existsByProjectIdAndTypeAndResourceId(
                event.projectId(), type, event.collectionJobId())) {
            return;
        }
        ProjectMember target = projectMemberRepository.findAllByIdIn(
                        List.of(event.requestedByProjectMemberId())).stream()
                .filter(member -> isActiveProjectMember(member, event.projectId()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return;
        }
        deliver(List.of(target), target.getProject(), type,
                "활동 로그 수집이 완료되었습니다. 이제 계정을 매핑할 수 있습니다.",
                event.collectionJobId(), Map.of(
                        "projectId", event.projectId().toString(),
                        "resourceId", event.collectionJobId().toString(),
                        "type", type.name()));
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
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> pushUserIds = userIds.stream()
                .filter(userId -> notificationPushPolicy.isEnabled(userId, project.getId(), type))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<FcmToken> tokens = pushUserIds.isEmpty()
                ? List.of()
                : fcmTokenRepository.findAllByUserIdIn(pushUserIds);
        log.info("project_notification_delivery type={} projectId={} targetCount={} tokenCount={}",
                type, project.getId(), targets.size(), tokens.size());
        for (FcmToken token : tokens) {
            sendWithRetry(token.getToken(), project.getProjectName(), body, data, type, project.getId());
        }
    }

    private void sendWithRetry(
            String token,
            String title,
            String body,
            Map<String, String> data,
            NotificationType type,
            Long projectId
    ) {
        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                fcmGateway.send(new FcmMessage(token, title, body, data));
                log.info("project_notification_delivery_succeeded type={} projectId={}", type, projectId);
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
                if (!backoff(attempt)) {
                    return;
                }
            } catch (RuntimeException exception) {
                if (attempt > MAX_RETRIES) {
                    log.error("project_notification_delivery_failed type={} attempts={}", type, attempt, exception);
                    return;
                }
                if (!backoff(attempt)) {
                    return;
                }
            }
        }
    }

    private boolean backoff(int attempt) {
        try {
            Thread.sleep(200L * (1L << (attempt - 1)));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("project_notification_retry_interrupted attempt={}", attempt);
            return false;
        }
    }

    private String senderName(ProjectMember sender) {
        if (sender.getAnNickname() != null && !sender.getAnNickname().isBlank()) {
            return sender.getAnNickname();
        }
        String nickname = sender.getUser().getNickname();
        return nickname == null || nickname.isBlank() ? "프로젝트 멤버" : nickname;
    }

    private boolean isActiveProjectMember(ProjectMember member, Long projectId) {
        return member != null && member.getStatus() == MemberStatus.ACTIVE
                && member.getProject() != null && projectId.equals(member.getProject().getId());
    }
}
