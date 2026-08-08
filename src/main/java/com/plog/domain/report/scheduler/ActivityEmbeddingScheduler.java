package com.plog.domain.report.scheduler;

import com.plog.domain.report.service.ActivityEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 리포트 파이프라인 3단계(임베딩 생성)를 주기적으로 돌려 정제를 통과한 행의 벡터를 채운다.
 * <p>
 * 실제 로직은 {@link ActivityEmbeddingService#embedBatch()}에 있고 여기는 실행 주기만 담당한다.
 * {@link ActivityRefinementScheduler}와 마찬가지로 poll-delay-ms 기반 fixedDelay로 돈다.
 * <p>
 * 정제(1단계) 선행 의존성: 임베딩 대상 쿼리(selectClaimableEmbeddingActivities)가
 * noise_filtered=false로 이미 확정된 행만 조회하므로, 이 스케줄러가 정제 스케줄러보다 먼저 돌거나
 * 더 자주 돌아도 잘못된(미정제) 행을 집어가는 일은 없다 — 그저 가져올 행이 없어 0건으로 끝난다.
 * 그래도 정제가 끝나야 임베딩이 바로 이어받을 수 있으므로 기본 poll-delay는 정제(30초)보다
 * 넉넉하게(60초) 잡는다 — 외부 API 호출이 섞여 배치 1회 처리 시간 자체도 더 길기 때문이다.
 * <p>
 * embedBatch()는 429(호출 한도)를 만나면 스스로 배치를 멈추고 리스를 즉시 풀어주므로, 이 스케줄러는
 * 그 결과를 그대로 받아들이기만 하면 된다 — 별도의 백오프 로직이 필요 없다.
 * <p>
 * 기본값은 꺼짐이다. Gemini API 키 없이 로컬/CI에서 자동으로 켜지면 Stub 임베딩으로 대량의
 * embedding_model이 채워져버려, 이후 실제 키로 전환했을 때 재처리가 안 되는 행이 쌓인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plog.report.embedding.enabled", havingValue = "true")
public class ActivityEmbeddingScheduler {

    private final ActivityEmbeddingService activityEmbeddingService;

    @Scheduled(fixedDelayString = "${plog.report.embedding.poll-delay-ms:60000}")
    public void embed() {
        try {
            // 행 단위 실패(개별 embed() 실패)는 embedBatch() 안에서 흡수되고 WARN 로깅 후 계속된다.
            // 여기로 올라오는 건 선점(claim) 자체가 실패하는 등 배치 전체가 못 도는 경우다.
            int count = activityEmbeddingService.embedBatch();
            if (count > 0) {
                log.info("활동 로그 임베딩 스케줄 실행: count={}", count);
            }
        } catch (RuntimeException e) {
            log.error("활동 로그 임베딩 배치 실패", e);
        }
    }
}