package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatMessage;

import org.springframework.data.domain.Pageable;
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

    // 읽음 처리 요청의 lastReadMessageId가 실제로 이 채팅방 소속 메시지인지 검증하기 위한 조회.
    Optional<ChatMessage> findByIdAndChatRoomIdAndMessageSequenceIsNotNull(Long id, Long chatRoomId);

    @Query("select m from ChatMessage m "
            + "left join fetch m.chatRoom "
            + "join fetch m.projectMember pm "
            + "join fetch pm.user "
            + "where m.id = :chatId")
    Optional<ChatMessage> findWithRoomAndSenderById(@Param("chatId") Long chatId);

    // 메시지 목록 조회 — messageSequence 기준 커서 페이지네이션.
    // before가 null이면 최신 메시지부터, 있으면 그보다 이전(과거) 메시지를 최신순으로 가져온다.
    // backfill 안 된 메시지(messageSequence null)는 목록에서 제외.
    @Query("select m from ChatMessage m "
            + "join fetch m.projectMember pm "
            + "join fetch pm.user "
            + "where m.chatRoom.id = :roomId "
            + "and m.messageSequence is not null "
            + "and (:before is null or m.messageSequence < :before) "
            + "order by m.messageSequence desc")
    List<ChatMessage> findPageBeforeCursor(
            @Param("roomId") Long roomId,
            @Param("before") Long before,
            Pageable pageable);
}
