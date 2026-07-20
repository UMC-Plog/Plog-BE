package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatRoomReadCursor;
import com.plog.domain.chat.repository.projection.ChatRoomUnreadCount;
import com.plog.domain.project.entity.MemberStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ChatRoomReadCursorRepository extends JpaRepository<ChatRoomReadCursor, Long> {

    @Query("select cursor from ChatRoomReadCursor cursor "
            + "where cursor.chatRoom.id = :roomId "
            + "and cursor.projectMember.user.id = :userId "
            + "and cursor.projectMember.status = :memberStatus "
            + "and cursor.projectMember.project = cursor.chatRoom.project")
    Optional<ChatRoomReadCursor> findAccessibleCursor(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update chat_room_read_cursors cursor "
            + "set last_read_message_sequence = message.message_sequence, "
            + "updated_at = current_timestamp "
            + "from chat_messages message, project_members member, chat_rooms room "
            + "where cursor.chat_room_id = :roomId "
            + "and cursor.project_member_id = member.project_member_id "
            + "and cursor.chat_room_id = room.chat_room_id "
            + "and member.user_id = :userId "
            + "and member.project_status = 'ACTIVE' "
            + "and member.project_id = room.project_id "
            + "and message.chat_id = :messageId "
            + "and message.chat_room_id = cursor.chat_room_id "
            + "and message.message_sequence is not null "
            + "and (cursor.last_read_message_sequence is null "
            + "or cursor.last_read_message_sequence < message.message_sequence)",
            nativeQuery = true)
    int advance(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId
    );

    @Query("select room.id as chatRoomId, count(message.id) as unreadCount "
            + "from ChatRoom room "
            + "join ProjectMember member on member.project = room.project "
            + "left join ChatRoomReadCursor cursor "
            + "on cursor.chatRoom = room and cursor.projectMember = member "
            + "left join ChatMessage message on message.chatRoom = room "
            + "and (cursor.lastReadMessageSequence is null "
            + "or message.messageSequence > cursor.lastReadMessageSequence) "
            + "where room.id = :roomId "
            + "and member.user.id = :userId "
            + "and member.status = :memberStatus "
            + "group by room.id")
    Optional<ChatRoomUnreadCount> findUnreadCount(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus
    );

}
