package com.plog.domain.chat.repository.projection;

public interface ChatRoomParticipantUnreadCount {

    Long getUserId();

    long getUnreadCount();
}