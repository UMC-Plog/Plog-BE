package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatRoomReadCursor;
import com.plog.domain.chat.repository.projection.ChatRoomParticipantUnreadCount;
import com.plog.domain.chat.repository.projection.ChatRoomUnreadCount;
import com.plog.domain.project.entity.MemberStatus;
import java.util.List;
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

    // 최초 읽음 처리 시 커서가 없으므로 미리 만들어 둔다.
    // ON CONFLICT DO NOTHING이라 동시 요청으로 두 번 INSERT되어도 유니크 제약 위반 예외 없이 안전하다
    // (Postgres는 문장 실패 시 트랜잭션 전체가 abort되므로 try-catch로 잡는 방식은 쓰지 않는다).
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into chat_room_read_cursors "
            + "(chat_room_id, project_member_id, last_read_message_sequence, created_at, updated_at) "
            + "values (:roomId, :projectMemberId, null, current_timestamp, current_timestamp) "
            + "on conflict (chat_room_id, project_member_id) do nothing",
            nativeQuery = true)
    int createIfAbsent(
            @Param("roomId") Long roomId,
            @Param("projectMemberId") Long projectMemberId
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
            + "and member.project_status = :memberStatus "
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
            @Param("messageId") Long messageId,
            @Param("memberStatus") String memberStatus
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

    // 방 참여자 "전원"의 unreadCount를 한 번에 조회한다.
    // 새 메시지 도착 시 참여자마다 findUnreadCount를 반복 호출하면 메시지 1건당 쿼리가 N번(N=참여자 수) 나가므로,
    // 그 대신 이 쿼리 1번으로 방 전체 참여자의 unreadCount를 묶어서 가져온다.
    @Query("select member.user.id as userId, count(message.id) as unreadCount "
            + "from ProjectMember member "
            + "join ChatRoom room on room.project = member.project "
            + "left join ChatRoomReadCursor cursor "
            + "on cursor.chatRoom = room and cursor.projectMember = member "
            + "left join ChatMessage message on message.chatRoom = room "
            + "and (cursor.lastReadMessageSequence is null "
            + "or message.messageSequence > cursor.lastReadMessageSequence) "
            + "where room.id = :roomId "
            + "and member.status = :memberStatus "
            + "group by member.user.id")
    List<ChatRoomParticipantUnreadCount> findUnreadCountsForRoom(
            @Param("roomId") Long roomId,
            @Param("memberStatus") MemberStatus memberStatus
    );

}