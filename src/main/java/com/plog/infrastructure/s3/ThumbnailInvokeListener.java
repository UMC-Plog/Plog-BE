package com.plog.infrastructure.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 첨부 확정이 커밋된 뒤에 Lambda 를 부른다.
 * <p>
 * {@code @Async} + AFTER_COMMIT 은 ChatMessageBroadcastListener 가 이미 쓰는 패턴이다.
 * {@code @EnableAsync} 는 S3Configuration 에 이미 붙어 있다.
 * <p>
 * 예외를 삼킨다. 여기서 터지면 채팅 전송은 이미 커밋됐는데 로그만 시끄러워지고,
 * 재시도는 ThumbnailScheduler.requestPending() 이 어차피 담당한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ThumbnailInvokeListener {

    private final ThumbnailInvoker thumbnailInvoker;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThumbnailRequested(ThumbnailRequestedEvent event) {
        try {
            thumbnailInvoker.request(event.uploadedFileId());
        } catch (RuntimeException exception) {
            log.warn("thumbnail_request_failed uploadedFileId={}",
                    event.uploadedFileId(), exception);
        }
    }
}
