package com.plog.domain.chat.entity;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_messages", indexes = {
        @Index(
                name = "idx_chat_message_room_sequence",
                columnList = "chat_room_id, message_sequence",
                unique = true
        )
})
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Long id;

    // 기존 메시지 backfill 전까지 nullable로 유지한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    // 기존 메시지 backfill 전까지 nullable로 유지한다.
    @Column(name = "message_sequence")
    private Long messageSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    public static ChatMessage create(
            ChatRoom chatRoom,
            ProjectMember projectMember,
            String message,
            long messageSequence
    ) {
        Long roomProjectId = chatRoom.getProject().getId();
        Long memberProjectId = projectMember.getProject().getId();
        if (roomProjectId == null || !roomProjectId.equals(memberProjectId)) {
            throw new IllegalArgumentException("채팅방과 프로젝트 멤버의 프로젝트가 일치해야 합니다.");
        }
        if (messageSequence <= 0L) {
            throw new IllegalArgumentException("메시지 순번은 1 이상이어야 합니다.");
        }
        return ChatMessage.builder()
                .chatRoom(chatRoom)
                .projectMember(projectMember)
                .message(message)
                .messageSequence(messageSequence)
                .build();
    }
}
