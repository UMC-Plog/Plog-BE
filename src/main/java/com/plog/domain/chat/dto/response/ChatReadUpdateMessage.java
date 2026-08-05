package com.plog.domain.chat.dto.response;

// /user/queue/chat-update push 전용 DTO. REST 응답(ChatReadResponse)과는 별개 —
// 같은 채널에서 ChatRoomSummaryMessage(type: ROOM_SUMMARY)와 구분되어야 하므로 type 필드를 가진다.
public record ChatReadUpdateMessage(
        String type,
        Long roomId,
        Long lastReadMessageSequence,
        long unreadMessageCount
) {
    private static final String TYPE = "READ_UPDATE";

    public static ChatReadUpdateMessage of(Long roomId, Long lastReadMessageSequence, long unreadMessageCount) {
        return new ChatReadUpdateMessage(TYPE, roomId, lastReadMessageSequence, unreadMessageCount);
    }
}