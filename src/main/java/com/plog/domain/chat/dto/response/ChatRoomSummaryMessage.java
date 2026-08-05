package com.plog.domain.chat.dto.response;

// /user/queue/chat-update push 전용 DTO. 새 메시지 도착 시 발신자를 제외한 방 참여자 전원에게 전송되어
// 채팅방 목록의 unreadCount/최신 메시지 미리보기를 실시간으로 갱신하는 데 쓰인다.
//
// 클라이언트 계약: 이 push는 @Async + 재시도로 발송되어 같은 방에 대한 도착 순서가 보장되지 않는다.
// 클라이언트는 방(roomId)별로 마지막에 반영한 messageSequence를 보관하고, 새로 들어온 push의
// messageSequence가 보관값보다 클 때만 목록(unreadCount/latestMessage)을 갱신해야 한다.
// 더 작은 순서값이 늦게 도착해도 그대로 반영하면 최신 상태가 과거 값으로 덮어써진다.
public record ChatRoomSummaryMessage(
        String type,
        Long roomId,
        Long messageSequence,
        String latestMessage,
        long unreadMessageCount
) {
    private static final String TYPE = "ROOM_SUMMARY";

    public static ChatRoomSummaryMessage of(
            Long roomId, Long messageSequence, String latestMessage, long unreadMessageCount) {
        return new ChatRoomSummaryMessage(TYPE, roomId, messageSequence, latestMessage, unreadMessageCount);
    }
}