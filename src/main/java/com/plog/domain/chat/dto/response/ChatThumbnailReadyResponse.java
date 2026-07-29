package com.plog.domain.chat.dto.response;

/**
 * 썸네일 준비 완료 push 페이로드. 목적지가
 * {@code /topic/chat-rooms/{roomId}/attachments} 라 roomId 는 싣지 않는다.
 * <p>
 * 메시지 스트림({@code /topic/chat-rooms/{roomId}})과 목적지를 나눈 이유는, 메시지가
 * 아닌 페이로드가 같은 스트림에 섞이면 프론트의 append 로직 앞에 가드가 필요해지고
 * 실수하면 빈 메시지 버블이 뜨기 때문이다.
 */
public record ChatThumbnailReadyResponse(
        Long chatAttachmentId,
        String thumbnailUrl
) {
}
