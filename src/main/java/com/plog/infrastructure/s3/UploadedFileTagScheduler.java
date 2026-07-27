package com.plog.infrastructure.s3;

import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드 객체의 상태를 S3 태그에 반영하고, 방치된 행을 회수한다.
 * <p>
 * DB 만 조회한다. S3 버킷 스캔은 하지 않는다 — 객체 수가 늘면 비용과 시간이
 * 선형으로 커지고, 레지스트리가 이미 전수를 알고 있어 스캔할 이유가 없다.
 */
@Component
@ConditionalOnProperty(name = "plog.s3.tag-scheduler.enabled", havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class UploadedFileTagScheduler {

    private static final Limit BATCH = Limit.of(200);
    private static final int ABANDONED_PENDING_DAYS = 1;
    /** S3 Lifecycle 의 orphaned 만료(7일)보다 길어야 한다. 먼저 지우면 재시도 근거를 잃는다. */
    private static final int RELEASED_ROW_RETENTION_DAYS = 14;

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;

    /**
     * 의도적으로 @Transactional 을 붙이지 않는다. S3 호출은 건당 수백 ms 가 걸릴 수 있어,
     * 배치 전체를 한 트랜잭션으로 묶으면 최대 200회의 블로킹 네트워크 호출 동안 DB
     * 커넥션을 점유한다. 조회와 기록만 각자 짧은 트랜잭션(리포지토리)으로 처리한다.
     */
    @Scheduled(fixedDelay = 30_000)
    public void retryTagging() {
        List<UploadedFile> targets = uploadedFileRepository.findByTaggedAtIsNull(BATCH);
        for (UploadedFile file : targets) {
            // 한 건의 S3 오류가 배치 전체를 막으면 그 행이 큐를 영구히 점유한다.
            // AccessDenied·5xx·네트워크 오류는 이 행만 건너뛰고 다음 틱에 다시 시도한다.
            try {
                boolean applied = fileStorageService.applyState(
                        file.getFileKey(), file.getStatus(), file.getOwnerId());
                if (!applied) {
                    // 객체가 없다 = 클라이언트가 PUT 을 안 했거나 이미 만료됐다.
                    // 실패로 두면 taggedAt 이 영원히 null 이라 매 틱 재시도한다.
                    log.info("s3_tag_target_missing fileKey={} status={}",
                            file.getFileKey(), file.getStatus());
                }
                uploadedFileRepository.markTagged(file.getId(), TimeUtil.nowUtc());
            } catch (RuntimeException exception) {
                log.warn("s3_tag_failed fileKey={} status={}",
                        file.getFileKey(), file.getStatus(), exception);
            }
        }
    }

    @Scheduled(cron = "0 10 3 * * *")
    @Transactional
    public void reclaimAbandonedPending() {
        LocalDateTime now = TimeUtil.nowUtc();
        int reclaimed = uploadedFileRepository.releaseAbandonedPending(
                now, now.minusDays(ABANDONED_PENDING_DAYS),
                UploadedFileStatus.ORPHANED, UploadedFileStatus.PENDING);
        if (reclaimed > 0) {
            log.info("uploaded_file_pending_reclaimed count={}", reclaimed);
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeReleasedRows() {
        LocalDateTime threshold = TimeUtil.nowUtc().minusDays(RELEASED_ROW_RETENTION_DAYS);
        List<UploadedFile> targets = uploadedFileRepository.findByStatusAndReleasedAtBefore(
                UploadedFileStatus.ORPHANED, threshold, BATCH);
        uploadedFileRepository.deleteAll(targets);
    }
}
