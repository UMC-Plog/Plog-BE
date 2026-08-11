package com.plog.domain.notification.service;

import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.exception.NotificationErrorCode;
import com.plog.domain.notification.repository.NotificationRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        validateAuthenticatedUser(userId);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        validateAuthenticatedUser(userId);
        notificationRepository.markAllAsReadByUserId(userId);
    }

    private void validateAuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
    }
}
