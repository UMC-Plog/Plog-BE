package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.project.entity.MemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
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
}
