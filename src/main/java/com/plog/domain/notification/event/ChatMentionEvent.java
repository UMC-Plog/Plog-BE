package com.plog.domain.notification.event;

import java.util.List;

public record ChatMentionEvent(
        Long projectId,
        Long chatId,
        Long senderMemberId,
        List<Long> mentionMemberIds,
        String messagePreview
) {
    public ChatMentionEvent {
        mentionMemberIds = mentionMemberIds == null ? List.of() : List.copyOf(mentionMemberIds);
    }
}
