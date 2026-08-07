package com.plog.domain.notification.repository;

import com.plog.domain.notification.entity.NotificationProjectSetting;
import com.plog.domain.notification.entity.NotificationType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationProjectSettingRepository extends JpaRepository<NotificationProjectSetting, Long> {
    Optional<NotificationProjectSetting> findByUserIdAndProjectIdAndType(
            Long userId, Long projectId, NotificationType type);

    @Query("select setting from NotificationProjectSetting setting "
            + "where setting.user.id = :userId and setting.project.id in :projectIds")
    List<NotificationProjectSetting> findAllByUserIdAndProjectIdIn(
            @Param("userId") Long userId,
            @Param("projectIds") Collection<Long> projectIds);
}
