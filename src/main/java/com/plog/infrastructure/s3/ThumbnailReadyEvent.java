package com.plog.infrastructure.s3;

/**
 * 썸네일이 준비됐다는 신호. <b>infrastructure 가 domain 을 모르게 하기 위한 경계다.</b>
 * <p>
 * 스케줄러가 채팅 도메인의 STOMP push 를 직접 부르면 infrastructure → domain 의존이
 * 생겨 방향이 뒤집힌다. 이벤트만 던지고 domain.chat 이 구독한다.
 */
public record ThumbnailReadyEvent(Long uploadedFileId) {
}
