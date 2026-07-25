package com.plog.domain.chat.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TODO(팀 확인): ERD가 placeholder(Field/Field) 상태라 post_attachments 구조를 참고해 임시 확정.
//  채팅 첨부에 LINK/EXTERNAL 유형이 필요하면 attachment_type 컬럼 추가.
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "chat_attachments", indexes = {
        @Index(name = "idx_chat_attachment_chat_id", columnList = "chat_id")
})
public class ChatAttachment extends BaseEntity {

    // ERD는 "id"였으나 다른 테이블 네이밍({table}_id)과 통일
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatMessage chatMessage;

    // 직접 업로드한 파일만 지원하므로 attachmentType 구분 없이 filekey 단일 컬럼으로 둔다.
    @Column(name = "file_key", nullable = false, length = 512)
    private String fileKey;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    public static ChatAttachment create(ChatMessage chatMessage, String fileKey, String fileName, Long fileSize) {
        return ChatAttachment.builder()
                .chatMessage(chatMessage)
                .fileKey(fileKey)
                .fileName(fileName)
                .fileSize(fileSize)
                .build();
    }
}
