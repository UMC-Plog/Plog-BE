package com.plog.domain.notification.event;

import java.util.List;

public record ChatMessageNotificationEvent(
        Long projectId,
        Long roomId,
        Long chatId,
        Long senderMemberId,
        List<Long> mentionMemberIds,
        String messagePreview
) {
    public ChatMessageNotificationEvent {
        mentionMemberIds = mentionMemberIds == null ? List.of() : List.copyOf(mentionMemberIds);
    }
}
