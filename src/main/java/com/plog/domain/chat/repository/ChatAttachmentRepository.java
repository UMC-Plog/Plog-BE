package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    List<ChatAttachment> findAllByChatMessageIdOrderByIdAsc(Long chatMessageId);
}