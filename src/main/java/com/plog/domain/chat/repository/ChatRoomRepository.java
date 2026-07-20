package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByProjectId(Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ChatRoom room where room.id = :roomId")
    Optional<ChatRoom> findByIdForMessageAppend(@Param("roomId") Long roomId);

    @Query("select room from ChatRoom room where room.id = :roomId and exists ("
            + "select member.id from ProjectMember member "
            + "where member.project = room.project and member.user.id = :userId "
            + "and member.status = :memberStatus)")
    Optional<ChatRoom> findAccessibleRoom(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus
    );

    @Query(
            value = "select room.project.id as projectId, "
                    + "room.project.projectName as projectName, "
                    + "room.id as roomId, "
                    + "latest.message as latestMessage, "
                    + "latest.createdAt as latestMessageAt, "
                    + "count(unread.id) as unreadMessageCount "
                    + "from ChatRoom room "
                    + "join ProjectMember member on member.project = room.project "
                    + "and member.user.id = :userId and member.status = :memberStatus "
                    + "left join ChatMessage latest on latest.chatRoom = room "
                    + "and latest.messageSequence = room.lastMessageSequence "
                    + "left join ChatRoomReadCursor cursor on cursor.chatRoom = room "
                    + "and cursor.projectMember = member "
                    + "left join ChatMessage unread on unread.chatRoom = room "
                    + "and unread.messageSequence is not null "
                    + "and (cursor.lastReadMessageSequence is null "
                    + "or unread.messageSequence > cursor.lastReadMessageSequence) "
                    + "group by room.project.id, room.project.projectName, room.id, "
                    + "latest.message, latest.createdAt "
                    + "order by case when latest.createdAt is null then 1 else 0 end, "
                    + "latest.createdAt desc, room.id asc",
            countQuery = "select count(room.id) from ChatRoom room "
                    + "where exists (select member.id from ProjectMember member "
                    + "where member.project = room.project "
                    + "and member.user.id = :userId and member.status = :memberStatus)"
    )
    Page<ChatChannelSummary> findChannelPage(
            @Param("userId") Long userId,
            @Param("memberStatus") MemberStatus memberStatus,
            Pageable pageable
    );
}
