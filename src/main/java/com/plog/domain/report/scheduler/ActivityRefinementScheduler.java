package com.plog.domain.report.scheduler;

import com.plog.domain.report.service.ActivityRefinementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 리포트 파이프라인 1단계(Rule 기반 정제)를 주기적으로 돌려 밀려있는 미정제 행을 비워나간다.
 * <p>
 * 실제 로직은 {@link ActivityRefinementService#refineNoiseBatch()}에 있고 여기는 실행 주기만
 * 담당한다 — ReportGenerationScheduler와 같은 이유로, 이 빈이 토글로 꺼진 로컬·테스트에서도
 * 배치를 수동으로 돌려볼 수 있다.
 * <p>
 * ReportGenerationScheduler(하루 한 번 cron)와 달리 이 배치는 계속 돌며 큐를 조금씩 비워나가는
 * 구조라 IntegrationCollectionJobWorker와 같이 poll-delay-ms 기반 fixedDelay로 돈다.
 * <p>
 * 기본값은 꺼짐이다. 정제 규칙(ActivityContentRefiner)이 운영 데이터로 아직 검증되지 않은
 * 상태에서 자동으로 켜지면 원인 파악 없이 대량의 noiseFiltered가 잘못 확정될 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plog.report.refinement.enabled", havingValue = "true")
public class ActivityRefinementScheduler {

    private final ActivityRefinementService activityRefinementService;

    @Scheduled(fixedDelayString = "${plog.report.refinement.poll-delay-ms:30000}")
    public void refine() {
        try {
            // 행 단위 실패는 여기까지 오지 않는다(정제는 순수 계산이라 개별 실패가 없다).
            // 여기로 올라오는 건 대상 조회 실패처럼 배치 전체가 못 도는 경우다.
            int count = activityRefinementService.refineNoiseBatch();
            if (count > 0) {
                log.info("활동 로그 정제 스케줄 실행: count={}", count);
            }
        } catch (RuntimeException e) {
            log.error("활동 로그 정제 배치 실패", e);
        }
    }
}