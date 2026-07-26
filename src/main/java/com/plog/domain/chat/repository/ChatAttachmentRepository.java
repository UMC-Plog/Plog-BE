package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatAttachment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    List<ChatAttachment> findAllByChatMessageIdOrderByIdAsc(Long chatMessageId);

    // 메시지 목록 조회 시 N+1 방지 — 여러 메시지의 첨부파일을 한 번에 조회
    List<ChatAttachment> findAllByChatMessageIdInOrderByIdAsc(Collection<Long> chatMessageIds);
}