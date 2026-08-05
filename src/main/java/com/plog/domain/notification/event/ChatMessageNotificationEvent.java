package com.plog.domain.notification.event;

import java.util.List;

public record ChatMessageNotificationEvent(
        Long projectId,
        Long roomId,
        Long chatId,
        Long senderMemberId,
        List<Long> targetMemberIds,
        String messagePreview
) {
    public ChatMessageNotificationEvent {
        targetMemberIds = targetMemberIds == null ? List.of() : List.copyOf(targetMemberIds);
    }
}
