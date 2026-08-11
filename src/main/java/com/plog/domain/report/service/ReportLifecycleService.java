package com.plog.domain.report.service;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.ReportRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트의 생성(시작) 지점을 한 곳으로 모은다. 지금 호출부는 평가 종료 확정 시점
 * (ProjectStatusService)이고, 여기에 마감일 기준 배치(ReportGenerationScheduler)가 붙는다.
 * 두 경로가 같은 프로젝트에 리포트를 두 번 만들지 않도록 멱등성은 이 서비스가 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportLifecycleService {

    private final ReportRepository reportRepository;
    private final ProjectRepository projectRepository;

    /**
     * 유예 기간이 끝난 프로젝트의 평가를 닫고 리포트를 시작한다. 배치가 프로젝트 1건마다 호출하며,
     * 이 메서드 하나가 곧 트랜잭션 1개다 — 실패해도 이 프로젝트만 롤백되고 배치는 계속 간다.
     * <p>
     * 평가를 함께 닫는 이유: {@code isEvaluatingState} 는 "미완료 && endDay 지남"이라 프로젝트를
     * COMPLETED 로 바꾸지 않으면 리포트가 발행된 뒤에도 동료 평가가 계속 들어온다.
     * 그러면 이미 확정된 리포트의 근거 데이터가 나중에 바뀐다.
     * <p>
     * 프로젝트 행을 비관적 락으로 선점하는 것이 멱등성의 근거다 — 사용자 요청 경로
     * ({@code ProjectStatusService})도 같은 락을 잡으므로 둘이 동시에 리포트를 만들 수 없다.
     *
     * @return 새로 시작한 리포트. 이미 있어서 건너뛰었으면 {@link Optional#empty()}
     */
    @Transactional
    public Optional<Report> closeEvaluationAndStart(Long projectId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalStateException(
                        "리포트 생성 대상 프로젝트를 찾을 수 없습니다. projectId=" + projectId));
        if (!project.isCompleted()) {
            project.complete();
        }
        return startFor(project);
    }

    /**
     * 프로젝트의 리포트를 시작한다. 상태와 무관하게 리포트가 이미 하나라도 있으면
     * 아무것도 하지 않고 빈 값을 돌려준다 — 사용자 재생성 요청은 지원하지 않는다.
     * <p>
     * 호출부의 프로젝트 행 락으로 정상 경로를 직렬화하고, DB의 project_id 유니크 제약으로
     * 다른 진입점이나 실수로 인한 중복 insert까지 최종 차단한다.
     *
     * @return 새로 시작한 리포트. 이미 있어서 건너뛰었으면 {@link Optional#empty()}
     */
    @Transactional
    public Optional<Report> startFor(Project project) {
        if (project == null || project.getId() == null) {
            throw new IllegalArgumentException("리포트를 시작하려면 저장된 프로젝트가 필요합니다.");
        }
        if (reportRepository.existsByProjectId(project.getId())) {
            return Optional.empty();
        }
        Report report = reportRepository.save(Report.start(project));
        log.info("리포트 생성: reportId={}, projectId={}", report.getId(), project.getId());
        return Optional.of(report);
    }
}
