package com.plog.domain.notification.dto.response;

import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.global.util.TimeUtil;
import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String content,
        Long projectId,
        String projectName,
        Long resourceId,
        boolean isRead,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getContent(),
                notification.getProject().getId(),
                notification.getProject().getProjectName(),
                notification.getResourceId(),
                notification.isRead(),
                TimeUtil.toInstant(notification.getCreatedAt())
        );
    }
}