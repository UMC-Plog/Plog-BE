package com.plog.domain.chat.dto.response;

// /user/queue/chat-update push 전용 DTO. 새 메시지 도착 시 발신자를 제외한 방 참여자 전원에게 전송되어
// 채팅방 목록의 unreadCount/최신 메시지 미리보기를 실시간으로 갱신하는 데 쓰인다.
public record ChatRoomSummaryMessage(
        String type,
        Long roomId,
        String latestMessage,
        long unreadMessageCount
) {
    private static final String TYPE = "ROOM_SUMMARY";

    public static ChatRoomSummaryMessage of(Long roomId, String latestMessage, long unreadMessageCount) {
        return new ChatRoomSummaryMessage(TYPE, roomId, latestMessage, unreadMessageCount);
    }
}