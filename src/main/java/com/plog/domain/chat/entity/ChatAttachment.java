package com.plog.domain.chat.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "chat_attachments")
public class ChatAttachment extends BaseEntity {

    // ERD는 "id"였으나 다른 테이블 네이밍({table}_id)과 통일
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_attachment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatMessage chatMessage;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private String fileSize;
}
