package com.plog.domain.chat.repository.projection;

public interface ChatRoomUnreadCount {

    Long getChatRoomId();

    long getUnreadCount();
}
