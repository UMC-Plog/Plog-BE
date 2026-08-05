package com.plog.domain.report.scheduler;

import com.plog.domain.report.service.ReportBatchResult;
import com.plog.domain.report.service.ReportBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 평가 유예가 끝난 프로젝트의 리포트를 하루 한 번 자동으로 시작한다.
 * <p>
 * 실제 로직은 {@link ReportBatchService} 에 있고 여기는 실행 시점만 담당한다 —
 * 그래야 이 빈이 토글로 꺼진 로컬·테스트에서도 배치를 수동으로 돌려볼 수 있다.
 * <p>
 * 기본값은 꺼짐이다. 리포트 생성 파이프라인(LLM·발행)이 완성되기 전에 켜면 GENERATING 상태로만
 * 남는 리포트가 쌓인다. 파이프라인이 끝난 뒤 배포 환경변수로 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plog.report.scheduler.enabled", havingValue = "true")
public class ReportGenerationScheduler {

    private final ReportBatchService reportBatchService;

    // 컨테이너 TZ는 UTC 고정이다(docker-compose.yml의 TZ=UTC) — UTC 기준 매일 03:40.
    // 기존 새벽 배치(03:00 탈퇴 파기 / 03:20 S3 회수 / 03:30 소셜 티켓 파기)와 시간을 벌려
    // 스케줄러 스레드풀(size=4)에서 서로 밀리지 않게 한다.
    @Scheduled(cron = "${plog.report.scheduler.cron:0 40 3 * * *}")
    public void startDueReports() {
        try {
            // 프로젝트 건별 실패는 여기까지 오지 않는다(배치 안에서 격리 + ERROR 로깅).
            // 여기로 올라오는 건 대상 조회 실패처럼 배치 전체가 못 도는 경우다.
            ReportBatchResult result = reportBatchService.startDueReports();
            if (result.hasWork()) {
                log.info("리포트 자동 생성 스케줄 실행: {}", result);
            }
        } catch (RuntimeException e) {
            log.error("리포트 자동 생성 배치 실패", e);
        }
    }
}
