package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findAllByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    List<ChatMessage> findAllByChatRoomIdOrderByMessageSequenceAsc(Long chatRoomId);

    Optional<ChatMessage> findByChatRoomIdAndProjectMemberIdAndClientMessageId(
            Long chatRoomId, Long projectMemberId, String clientMessageId);
}
