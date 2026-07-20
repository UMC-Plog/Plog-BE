package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatRoomReadCursor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomReadCursorRepository extends JpaRepository<ChatRoomReadCursor, Long> {
}
