package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.entity.FcmToken;
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
import com.plog.domain.user.entity.User;
import com.plog.global.util.AfterCommitExecutor;
import com.plog.infrastructure.fcm.FcmGateway;
import com.plog.infrastructure.fcm.FcmDeliveryException;
import com.plog.infrastructure.fcm.FcmMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProjectNotificationServiceTest {
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private FcmTokenRepository fcmTokenRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private FcmGateway fcmGateway;
    @Mock private NotificationPushPolicy notificationPushPolicy;
    private ProjectNotificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(notificationPushPolicy.isEnabled(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(NotificationType.class))).thenReturn(true);
        service = new ProjectNotificationService(
                projectMemberRepository, fcmTokenRepository, notificationRepository, fcmGateway,
                notificationPushPolicy, new AfterCommitExecutor());
    }

    @Test
    void 일반_채팅은_메시지_저장_시점에_확정된_활성_멤버에게만_저장한다() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(10L);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember sender = member(1L, 101L, project, "보낸이");
        ProjectMember target = member(3L, 103L, project, "수신자");
        when(projectMemberRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(sender, target));
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("target-token");
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));

        service.sendChatMessage(new ChatMessageNotificationEvent(
                10L, 20L, 30L, 1L, List.of(3L), "확인 부탁"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(notification -> {
            assertThat(notification.getUser().getId()).isEqualTo(103L);
            assertThat(notification.getType()).isEqualTo(NotificationType.CHAT_MESSAGE);
            assertThat(notification.getResourceId()).isEqualTo(30L);
        });

        ArgumentCaptor<FcmMessage> messageCaptor = ArgumentCaptor.forClass(FcmMessage.class);
        verify(fcmGateway).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().token()).isEqualTo("target-token");
        assertThat(messageCaptor.getValue().data()).containsEntry("type", "CHAT_MESSAGE")
                .containsEntry("projectId", "10")
                .containsEntry("roomId", "20")
                .containsEntry("resourceId", "30");
    }

    @Test
    void 피어_평가_시작_이벤트는_활성_멤버에게_알림을_저장한다() {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember first = member(1L, 101L, project, "첫째");
        ProjectMember second = member(2L, 102L, project, "둘째");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of());

        service.sendPeerEvaluationStarted(new PeerEvaluationStartedEvent(10L, null));

        assertSavedNotifications(NotificationType.PEER_EVALUATION_STARTED, null, 2);
    }

    @Test
    void 리포트_발행_이벤트는_활성_멤버에게_리포트_ID와_함께_저장한다() {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember first = member(1L, 101L, project, "첫째");
        ProjectMember second = member(2L, 102L, project, "둘째");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of());

        service.sendReportPublished(new ReportPublishedEvent(10L, 20L));

        assertSavedNotifications(NotificationType.REPORT_PUBLISHED, 20L, 2);
    }

    @Test
    void 활동_로그_수집_완료는_요청자에게만_잡_ID와_함께_알림을_보낸다() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(10L);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember requester = member(1L, 101L, project, "요청자");
        when(projectMemberRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(requester));
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("requester-token");
        when(fcmTokenRepository.findAllByUserIdIn(java.util.Set.of(101L))).thenReturn(List.of(token));

        service.sendIntegrationCollectionCompleted(
                new IntegrationCollectionCompletedEvent(10L, 42L, 1L));

        assertSavedNotifications(NotificationType.INTEGRATION_COLLECTION_COMPLETED, 42L, 1);
        ArgumentCaptor<FcmMessage> messageCaptor = ArgumentCaptor.forClass(FcmMessage.class);
        verify(fcmGateway).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().data())
                .containsEntry("type", "INTEGRATION_COLLECTION_COMPLETED")
                .containsEntry("projectId", "10")
                .containsEntry("resourceId", "42");
    }

    @Test
    void 동일한_수집_잡의_완료_알림은_중복_발송하지_않는다() {
        when(notificationRepository.existsByProjectIdAndTypeAndResourceId(
                10L, NotificationType.INTEGRATION_COLLECTION_COMPLETED, 42L)).thenReturn(true);

        service.sendIntegrationCollectionCompleted(
                new IntegrationCollectionCompletedEvent(10L, 42L, 1L));

        verify(notificationRepository, org.mockito.Mockito.never()).saveAll(anyCollection());
        verify(fcmGateway, org.mockito.Mockito.never()).send(any(FcmMessage.class));
    }

    @Test
    void 공지_지정_이벤트는_작성자를_제외한_활성_멤버에게_저장하고_설정이_켜진_대상만_push한다() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(10L);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember publisher = member(1L, 101L, project, "작성자");
        ProjectMember enabled = member(2L, 102L, project, "수신자");
        ProjectMember disabled = member(3L, 103L, project, "알림 끈 수신자");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(publisher, enabled, disabled));
        when(notificationPushPolicy.isEnabled(103L, 10L, NotificationType.NOTICE)).thenReturn(false);
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("notice-token");
        when(fcmTokenRepository.findAllByUserIdIn(java.util.Set.of(102L))).thenReturn(List.of(token));

        service.sendNoticePublished(new NoticePublishedEvent(10L, 20L, 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> notificationCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue())
                .extracting(notification -> notification.getUser().getId())
                .containsExactly(102L, 103L);
        ArgumentCaptor<FcmMessage> messageCaptor = ArgumentCaptor.forClass(FcmMessage.class);
        verify(fcmGateway).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().data())
                .containsEntry("type", "NOTICE")
                .containsEntry("projectId", "10")
                .containsEntry("resourceId", "20");
    }

    @Test
    void 일시적_FCM_실패는_최대_세_번_재시도한다() {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember target = member(1L, 101L, project, "수신자");
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("retry-token");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(target));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));
        doThrow(new FcmDeliveryException(false, new RuntimeException("temporary")))
                .doThrow(new FcmDeliveryException(false, new RuntimeException("temporary")))
                .doThrow(new FcmDeliveryException(false, new RuntimeException("temporary")))
                .doNothing()
                .when(fcmGateway).send(any(FcmMessage.class));

        service.sendReportPublished(new ReportPublishedEvent(10L, 20L));

        verify(fcmGateway, times(4)).send(any(FcmMessage.class));
    }

    @Test
    void 무효_FCM_토큰은_재시도하지_않고_삭제한다() {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember target = member(1L, 101L, project, "수신자");
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("invalid-token");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(target));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));
        doThrow(new FcmDeliveryException(true, new RuntimeException("invalid")))
                .when(fcmGateway).send(any(FcmMessage.class));

        service.sendReportPublished(new ReportPublishedEvent(10L, 20L));

        verify(fcmGateway).send(any(FcmMessage.class));
        verify(fcmTokenRepository).deleteByToken("invalid-token");
    }

    @Test
    void FCM_재시도_대기가_중단되어도_예외를_전파하지_않고_저장한_알림을_유지한다() {
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember target = member(1L, 101L, project, "수신자");
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("retry-token");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(target));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));
        doThrow(new FcmDeliveryException(false, new RuntimeException("temporary")))
                .when(fcmGateway).send(any(FcmMessage.class));

        Thread.currentThread().interrupt();
        try {
            service.sendReportPublished(new ReportPublishedEvent(10L, 20L));
        } finally {
            Thread.interrupted();
        }

        verify(notificationRepository).saveAll(anyCollection());
        verify(fcmGateway).send(any(FcmMessage.class));
    }

    @Test
    void push_설정이_OFF여도_인앱_알림은_저장하고_FCM은_보내지_않는다() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(10L);
        ProjectMember sender = member(1L, 101L, project, "보낸이");
        ProjectMember target = member(2L, 102L, project, "수신자");
        when(projectMemberRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(sender, target));
        when(notificationPushPolicy.isEnabled(102L, 10L, NotificationType.CHAT_MESSAGE)).thenReturn(false);

        service.sendChatMessage(new ChatMessageNotificationEvent(
                10L, 20L, 30L, 1L, List.of(2L), "메시지"));

        verify(notificationRepository).saveAll(anyCollection());
        verify(fcmGateway, org.mockito.Mockito.never()).send(any(FcmMessage.class));
    }

    @Test
    void FCM은_알림_저장_트랜잭션이_커밋된_후에_발송한다() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(10L);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember target = member(1L, 101L, project, "수신자");
        FcmToken token = mock(FcmToken.class);
        when(token.getToken()).thenReturn("target-token");
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(target));
        when(fcmTokenRepository.findAllByUserIdIn(anyCollection())).thenReturn(List.of(token));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.sendReportPublished(new ReportPublishedEvent(10L, 20L));

            verify(notificationRepository).saveAll(anyCollection());
            verify(fcmGateway, org.mockito.Mockito.never()).send(any(FcmMessage.class));

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(fcmGateway).send(any(FcmMessage.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void assertSavedNotifications(NotificationType type, Long resourceId, int size) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(size).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo(type);
            assertThat(notification.getResourceId()).isEqualTo(resourceId);
        });
    }

    private ProjectMember member(Long id, Long userId, Project project, String nickname) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(user.getNickname()).thenReturn(nickname);
        ProjectMember member = mock(ProjectMember.class);
        lenient().when(member.getId()).thenReturn(id);
        lenient().when(member.getUser()).thenReturn(user);
        lenient().when(member.getProject()).thenReturn(project);
        lenient().when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        return member;
    }
}
