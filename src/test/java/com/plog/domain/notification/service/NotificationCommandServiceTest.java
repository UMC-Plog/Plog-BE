package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.exception.NotificationErrorCode;
import com.plog.domain.notification.repository.NotificationRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock private NotificationRepository notificationRepository;

    private NotificationCommandService notificationCommandService;

    @BeforeEach
    void setUp() {
        notificationCommandService = new NotificationCommandService(notificationRepository);
    }

    @Test
    void 본인_알림을_읽음_처리한다() {
        Notification notification = mock(Notification.class);
        when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(notification));

        notificationCommandService.markAsRead(1L, 10L);

        verify(notification).markRead();
    }

    @Test
    void 다른_사용자의_알림은_찾을_수_없는_알림으로_처리한다() {
        when(notificationRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationCommandService.markAsRead(1L, 10L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Test
    void 로그인_사용자의_읽지_않은_알림을_모두_읽음_처리한다() {
        notificationCommandService.markAllAsRead(1L);

        verify(notificationRepository).markAllAsReadByUserId(1L);
    }

    @Test
    void 인증_사용자가_없으면_읽음_처리를_거부한다() {
        assertThatThrownBy(() -> notificationCommandService.markAsRead(null, 10L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));
        assertThatThrownBy(() -> notificationCommandService.markAllAsRead(null))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verify(notificationRepository, never()).findByIdAndUserId(10L, null);
        verify(notificationRepository, never()).markAllAsReadByUserId(null);
    }
}
