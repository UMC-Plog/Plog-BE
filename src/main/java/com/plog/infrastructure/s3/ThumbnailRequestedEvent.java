package com.plog.infrastructure.s3;

/**
 * 첨부가 확정돼 썸네일이 필요해졌다는 신호.
 * <p>
 * confirmNew 는 {@code @Transactional} 이라 여기서 Lambda 를 부르면 ① 커밋 전에 Invoke 가
 * 나가 롤백 시 고아 썸네일이 생기고 ② 외부 I/O 가 DB 커넥션을 잡는다. 그래서 상태만
 * 찍고 이벤트를 던진 뒤, AFTER_COMMIT 리스너가 호출한다.
 * <p>
 * 엔티티가 아니라 id 만 싣는다. 리스너는 다른 스레드·다른 트랜잭션에서 돌기 때문에
 * (OSIV 도 꺼져 있다) 엔티티를 넘기면 지연 프록시에서 LazyInitializationException 이 난다.
 */
public record ThumbnailRequestedEvent(Long uploadedFileId) {
}
