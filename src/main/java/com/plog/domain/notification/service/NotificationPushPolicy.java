package com.plog.domain.notification.service;

import com.plog.domain.notification.entity.NotificationGlobalSetting;
import com.plog.domain.notification.entity.NotificationProjectSetting;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.repository.NotificationGlobalSettingRepository;
import com.plog.domain.notification.repository.NotificationProjectSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPushPolicy {
    private final NotificationGlobalSettingRepository globalRepository;
    private final NotificationProjectSettingRepository projectRepository;

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId, Long projectId, NotificationType type) {
        boolean globalEnabled = globalRepository.findByUserIdAndType(userId, type)
                .map(NotificationGlobalSetting::isEnabled)
                .orElse(true);
        if (!globalEnabled) {
            return false;
        }
        return projectRepository.findByUserIdAndProjectIdAndType(userId, projectId, type)
                .map(NotificationProjectSetting::isEnabled)
                .orElse(true);
    }
}
