package com.plog.domain.report.service;

import com.plog.domain.project.entity.Project;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 평가 유예가 끝난 프로젝트의 리포트를 자동으로 시작하는 배치.
 * <p>
 * 이 로직이 필요한 이유: 프로젝트 완료 전환은 {@code ProjectStatusService} 의 사용자 요청 경로에서만
 * 일어난다. 팀이 마감 후 앱을 열지 않으면 프로젝트는 영영 IN_PROGRESS 로 남고 리포트도 안 나온다.
 * <p>
 * 스케줄러({@code ReportGenerationScheduler})와 분리해 둔 이유는 <b>수동 트리거</b> 때문이다 —
 * 스케줄러 빈은 토글로 꺼지지만 이 서비스는 항상 등록되므로, 로컬·테스트에서 배치를 꺼둔 채로도
 * {@link #startDueReports()} 를 직접 호출해 검증할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportBatchService {

    /**
     * 한 회차에 처리할 프로젝트 수 상한. 밀린 프로젝트가 많아도 한 틱이 무한정 길어지지 않게 한다.
     * 남은 건은 다음 회차가 가져간다 — 쿼리 정렬이 endDay 오름차순이라 오래 밀린 것부터 빠진다.
     */
    private static final int BATCH_SIZE = 100;

    private final ReportRepository reportRepository;
    private final ReportLifecycleService reportLifecycleService;
    private final ReportGenerationService reportGenerationService;

    /**
     * 대상 프로젝트를 훑어 리포트를 시작한다. 스케줄러와 수동 트리거의 공통 진입점이다.
     * <p>
     * 일부러 {@code @Transactional} 을 걸지 않았다. 트랜잭션은 프로젝트 1건 단위
     * ({@link ReportLifecycleService#closeEvaluationAndStart})로 쪼개져 있어야
     * 1건 실패가 나머지를 되돌리지 않는다. 여기에 트랜잭션을 걸면 그 격리가 통째로 무너진다.
     * <p>
     * TODO: 인스턴스를 2대 이상으로 늘리면 같은 프로젝트를 동시에 집을 수 있다. 지금은
     *  {@code findByIdForUpdate} 의 행 락이 중복 생성을 막아주지만(뒤에 온 쪽은 skipped 로 떨어진다),
     *  낭비되는 조회가 늘어나므로 그때는 ShedLock 등 분산 락으로 실행 노드를 하나로 줄인다.
     */
    public ReportBatchResult startDueReports() {
        LocalDate today = TimeUtil.todayUtc();
        List<Project> dueProjects = reportRepository.findProjectsDueForReport(
                Project.latestEndDayWithClosedEvaluation(today),
                ReportStatus.restartBlockingStatuses(),
                PageRequest.of(0, BATCH_SIZE)
        );
        if (dueProjects.isEmpty()) {
            return ReportBatchResult.empty();
        }

        int started = 0;
        int skipped = 0;
        int failed = 0;
        for (Project project : dueProjects) {
            try {
                var startedReport = reportLifecycleService.closeEvaluationAndStart(project.getId());
                if (startedReport.isPresent()) {
                    started++;
                    // 여기서 이어서 생성까지 한다. 행만 만들고 끝내면 GENERATING 인 채로 남아
                    // 다음 회차에도 대상에서 빠지고(멱등성 기준에 GENERATING 이 포함된다) 영영 발행되지 않는다.
                    // 배치 스레드에서 동기로 도는 게 맞다 — 새벽에 순차 실행이라 서두를 이유가 없고,
                    // 비동기로 던지면 전용 풀(크기 1~2)에 대기만 쌓인다.
                    reportGenerationService.generate(startedReport.get().getId());
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                // 건별 격리 지점. 여기서 삼키지 않으면 프로젝트 1건 때문에 남은 대상이 전부 밀린다.
                failed++;
                log.error("리포트 자동 생성 실패: projectId={}", project.getId(), e);
            }
        }

        ReportBatchResult result = new ReportBatchResult(dueProjects.size(), started, skipped, failed);
        log.info("리포트 자동 생성 배치 완료: {}", result);
        return result;
    }
}
