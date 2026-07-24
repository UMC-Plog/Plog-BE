package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findAllByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    List<ChatMessage> findAllByChatRoomIdOrderByMessageSequenceAsc(Long chatRoomId);

    Optional<ChatMessage> findByChatRoomIdAndProjectMemberIdAndClientMessageId(
            Long chatRoomId, Long projectMemberId, String clientMessageId);

    @Query("select m from ChatMessage m "
            + "left join fetch m.chatRoom "
            + "join fetch m.projectMember pm "
            + "join fetch pm.user "
            + "where m.id = :chatId")
    Optional<ChatMessage> findWithRoomAndSenderById(@Param("chatId") Long chatId);
}
