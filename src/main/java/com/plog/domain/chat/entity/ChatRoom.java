package com.plog.domain.chat.entity;

import com.plog.domain.project.entity.Project;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_rooms", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_room_project", columnNames = "project_id")
})
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 기존 채팅방은 메시지 순번 backfill 후 현재 최댓값으로 채운다.
    @Builder.Default
    @Column(name = "last_message_sequence")
    private Long lastMessageSequence = 0L;

    public static ChatRoom create(Project project) {
        return ChatRoom.builder().project(project).build();
    }

    public long issueNextMessageSequence() {
        if (lastMessageSequence == null) {
            throw new IllegalStateException("기존 채팅방의 메시지 순번 backfill이 필요합니다.");
        }
        lastMessageSequence += 1L;
        return lastMessageSequence;
    }
}
