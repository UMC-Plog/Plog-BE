package com.plog.domain.notification.entity;

import com.plog.domain.user.entity.User;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "notification_global_settings", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_global_setting", columnNames = {"user_id", "notification_type"}))
public class NotificationGlobalSetting extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_global_setting_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private boolean enabled;

    public static NotificationGlobalSetting create(User user, NotificationType type, boolean enabled) {
        return NotificationGlobalSetting.builder()
                .user(user)
                .type(type)
                .enabled(enabled)
                .build();
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
