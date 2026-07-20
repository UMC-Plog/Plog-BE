package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatRoomReadCursor;
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
    @Query("update ChatRoomReadCursor cursor "
            + "set cursor.lastReadMessageId = :messageId "
            + "where cursor.chatRoom.id = :roomId "
            + "and cursor.projectMember.user.id = :userId "
            + "and cursor.projectMember.status = :memberStatus "
            + "and cursor.projectMember.project = cursor.chatRoom.project "
            + "and (cursor.lastReadMessageId is null or cursor.lastReadMessageId < :messageId) "
            + "and exists (select message.id from ChatMessage message "
            + "where message.id = :messageId and message.chatRoom = cursor.chatRoom)")
    int advance(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId,
            @Param("memberStatus") MemberStatus memberStatus
    );

}
