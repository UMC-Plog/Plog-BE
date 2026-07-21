package com.plog.domain.chat.repository.projection;

import java.time.LocalDateTime;

public interface ChatChannelSummary {

    Long getProjectId();

    String getProjectName();

    Long getRoomId();

    String getLatestMessage();

    LocalDateTime getLatestMessageAt();

    long getUnreadMessageCount();
}
