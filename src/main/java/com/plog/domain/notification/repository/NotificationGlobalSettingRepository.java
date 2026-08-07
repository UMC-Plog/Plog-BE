package com.plog.domain.notification.repository;

import com.plog.domain.notification.entity.NotificationGlobalSetting;
import com.plog.domain.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationGlobalSettingRepository extends JpaRepository<NotificationGlobalSetting, Long> {
    Optional<NotificationGlobalSetting> findByUserIdAndType(Long userId, NotificationType type);
    List<NotificationGlobalSetting> findAllByUserId(Long userId);
}
