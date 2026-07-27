package com.plog.domain.chat.entity;

import com.plog.global.common.BaseEntity;
import com.plog.infrastructure.s3.UploadedFile;
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
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_attachment_file", columnNames = "file_id")
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

    // 직접 업로드한 파일만 지원하므로 attachmentType 구분이 없다.
    // 파일명·크기는 레지스트리(uploaded_file)가 들고 있어 여기서 중복 보관하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private UploadedFile uploadedFile;

    public static ChatAttachment create(ChatMessage chatMessage, UploadedFile uploadedFile) {
        return ChatAttachment.builder()
                .chatMessage(chatMessage)
                .uploadedFile(uploadedFile)
                .build();
    }
}
