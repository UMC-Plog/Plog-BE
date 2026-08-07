package com.plog.domain.notification.dto;

import com.plog.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.Map;

public final class NotificationSettingsDto {
    private NotificationSettingsDto() {
    }

    public record Response(
            Map<NotificationType, Boolean> global,
            List<ProjectSettings> projects
    ) {
    }

    public record ProjectSettings(
            Long projectId,
            String projectName,
            Map<NotificationType, Boolean> settings
    ) {
    }
}
