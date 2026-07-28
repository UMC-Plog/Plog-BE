package com.plog.domain.notification.entity;

import com.plog.domain.project.entity.Project;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "notifications", indexes = {
        // 알림 센터 목록: 특정 유저의 알림을 최신순(id desc)으로 조회하기 위한 인덱스
        @Index(name = "idx_notification_user_id_desc", columnList = "user_id, notification_id")
})
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    // 알림 수신자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 현재는 모든 알림 타입이 프로젝트 스코프라 nullable = false.
    // 프로젝트와 무관한 알림 타입이 생기면 그때 nullable 허용으로 완화한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    // 발송 시점에 완성된 표시 문구를 그대로 저장한다.
    // 발신자 닉네임이 나중에 바뀌어도 알림 이력의 문구는 발송 당시 그대로 남아야 하므로
    // 조회 시점에 재조합하지 않고 원문을 스냅샷으로 보존한다.
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 딥링크용 리소스 식별자. 의미는 type에 따라 달라진다(CHAT_MENTION이면 chatId).
    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public static Notification create(User user, Project project, NotificationType type, String content, Long resourceId) {
        return Notification.builder()
                .user(user)
                .project(project)
                .type(type)
                .content(content)
                .resourceId(resourceId)
                .read(false)
                .build();
    }

    public void markRead() {
        this.read = true;
    }
}