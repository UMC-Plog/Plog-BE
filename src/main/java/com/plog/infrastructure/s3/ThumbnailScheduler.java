package com.plog.infrastructure.s3;

import com.plog.global.util.TimeUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 썸네일 생성의 아웃박스. uploaded_file 자체가 큐라 SQS 를 쓰지 않는다 —
 * 내구성과 재시도를 DB 가 이미 준다.
 * <p>
 * 두 잡으로 나눈 이유는 조건이 다른 두 집합을 한 쿼리로 못 긁기 때문이다.
 * 인덱스 idx_uploaded_file_thumbnail(thumbnail_status, thumbnail_at) 이 둘 다 커버한다.
 * <p>
 * 의도적으로 @Transactional 을 붙이지 않는다. S3 HeadObject 와 Lambda Invoke 가 건당
 * 수백 ms 라, 배치 전체를 한 트랜잭션으로 묶으면 블로킹 네트워크 호출 동안 DB 커넥션을
 * 점유한다(UploadedFileTagScheduler 와 같은 판단).
 */
@Component
@ConditionalOnProperty(name = "plog.thumbnail.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ThumbnailScheduler {

    private static final Limit BATCH = Limit.of(100);
    /** Invoke 시도 횟수 상한. 폴링 횟수가 아니다(INVOKE_TIMEOUT 참조). */
    private static final int MAX_ATTEMPTS = 3;
    /** Invoke 직후 곧바로 조회하면 아직 없는 게 당연하다. 한 박자 쉰다. */
    private static final Duration MIN_AGE = Duration.ofSeconds(1);
    /**
     * 이 시간이 지나도록 객체가 안 보이면 그 Invoke 는 무위로 끝난 것으로 본다.
     * <p>
     * 콜드스타트(sharp 11MB, arm64 512MB)가 2~5초라 3초 틱의 첫 조회는 거의 항상 빗나간다.
     * 그때 바로 실패로 처리하면 행이 폴링 대상에서 빠져(thumbnailAt = null 이 되어
     * thumbnailAt &lt; threshold 조건에 영영 안 걸린다) 30초짜리 안전망이 정상 경로가 되고,
     * 모든 이미지가 Lambda 를 두 번 실행하며 재시도 예산도 매번 1회씩 날아간다.
     * 창 안에서는 <b>DB 를 건드리지 않고</b> 다음 틱에 다시 본다.
     */
    private static final Duration INVOKE_TIMEOUT = Duration.ofSeconds(60);

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;
    private final ThumbnailInvoker thumbnailInvoker;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * <b>안전망 전용이다.</b> 정상 경로에서는 AFTER_COMMIT 리스너가 이미 thumbnail_at 을
     * 찍어 두므로 이 쿼리에 걸리는 행이 없다. 걸리는 경우는 두 가지다.
     * <ul>
     *   <li>커밋 직후 리스너가 돌기 전에 애플리케이션이 죽었다</li>
     *   <li>confirmReady() 가 "객체 없음"으로 판정해 at 을 null 로 되돌렸다(재요청)</li>
     * </ul>
     * 그래서 30초로 충분하다.
     */
    @Scheduled(fixedDelay = 30_000)
    public void requestPending() {
        List<UploadedFile> targets = uploadedFileRepository
                .findByThumbnailStatusAndThumbnailAtIsNull(ThumbnailStatus.PENDING, BATCH);
        for (UploadedFile file : targets) {
            try {
                thumbnailInvoker.request(file);
            } catch (RuntimeException exception) {
                log.warn("thumbnail_retry_invoke_failed fileId={}", file.getId(), exception);
            }
        }
    }

    /**
     * Lambda 실행이 1~3초라 그보다 촘촘히 돌 이유가 없고, 그보다 성기면 프론트의
     * 스켈레톤 시간이 눈에 띄기 시작한다. 평소에는 0건이라 인덱스 조회 한 번으로 끝난다.
     * <p>
     * 생성 중인 행은 INVOKE_TIMEOUT 동안 이 틱에 계속 걸리지만 <b>쓰기가 없다</b> —
     * HeadObject 한 번이 전부다.
     */
    @Scheduled(fixedDelay = 3_000)
    public void confirmReady() {
        LocalDateTime threshold = TimeUtil.nowUtc().minus(MIN_AGE);
        List<UploadedFile> targets = uploadedFileRepository
                .findByThumbnailStatusAndThumbnailAtBefore(
                        ThumbnailStatus.PENDING, threshold, BATCH);
        for (UploadedFile file : targets) {
            // 한 건의 S3 오류가 배치 전체를 막으면 그 행이 큐를 영구히 점유한다.
            try {
                confirmOne(file);
            } catch (RuntimeException exception) {
                log.warn("thumbnail_confirm_failed fileId={} thumbnailKey={}",
                        file.getId(), file.getThumbnailKey(), exception);
            }
        }
    }

    private void confirmOne(UploadedFile file) {
        if (fileStorageService.exists(file.getThumbnailKey())) {
            int applied = uploadedFileRepository.markThumbnailReady(
                    file.getId(), ThumbnailStatus.READY, ThumbnailStatus.PENDING);
            // 0 이면 다른 인스턴스가 이미 올리고 push 도 했다. 중복 push 를 막는다.
            if (applied == 1) {
                // 도메인에 직접 push 하지 않는다. 방향이 뒤집힌다(ThumbnailReadyEvent 참조).
                eventPublisher.publishEvent(new ThumbnailReadyEvent(file.getId()));
            }
            return;
        }
        // 아직 만드는 중일 수 있다. 창 안이면 DB 를 건드리지 않고 다음 틱에 다시 본다 —
        // 여기서 thumbnailAt 을 비우면 이 행이 폴링 대상에서 빠진다(INVOKE_TIMEOUT 참조).
        if (file.getThumbnailAt() != null
                && file.getThumbnailAt().isAfter(TimeUtil.nowUtc().minus(INVOKE_TIMEOUT))) {
            return;
        }
        uploadedFileRepository.recordThumbnailAttempt(
                file.getId(), MAX_ATTEMPTS, ThumbnailStatus.FAILED, ThumbnailStatus.PENDING);
    }
}
