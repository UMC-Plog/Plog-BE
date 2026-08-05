package com.plog.domain.report.service;

import com.plog.domain.notification.event.ReportPublishedEvent;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.report.config.ReportAsyncConfig;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.llm.ReportLlmGateway;
import com.plog.domain.report.llm.TeamLlmInput;
import com.plog.domain.report.llm.TeamReportText;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 리포트 1건을 끝까지 만드는 오케스트레이션 (5단계 LLM → 6단계 발행).
 *
 * <pre>
 *   멤버마다: 포트 4개 → 점수 확정 [트랜잭션] → LLM 호출 [트랜잭션 밖] → 텍스트 저장 [트랜잭션]
 *   전원 끝난 뒤: 팀 인사이트 1회 → 저장 → COMPLETED 전이 → ReportPublishedEvent
 * </pre>
 *
 * <b>클래스 레벨에 트랜잭션이 없는 것은 의도다.</b> LLM 호출이 멤버 수만큼 이어지므로
 * 전체를 한 트랜잭션으로 묶으면 커넥션을 수 분간 붙잡고, 멤버 1명 실패가 전원을 롤백시킨다.
 * <p>
 * 실패 정책: 멤버 1명의 LLM 실패는 그 멤버의 텍스트만 비우고 진행한다. 점수는 이미 저장돼 있어
 * 리포트는 여전히 쓸모가 있다. 전원이 실패한 경우에만 리포트를 FAILED 로 떨어뜨린다.
 * <p>
 * TODO: 멤버별 LLM 호출은 지금 순차다. 팀 규모가 커지면 병렬화를 검토하되,
 *  프로바이더 분당 요청 제한(RPM)을 함께 봐야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ReportRepository reportRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ReportMemberDataCollector dataCollector;
    private final ReportTextWriter textWriter;
    private final ReportLlmGateway llmGateway;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 비동기 생성. API 는 즉시 202 를 돌려주고 프론트는 조회 API 를 폴링한다 —
     * 멤버 수만큼의 LLM 호출을 HTTP 응답 안에서 기다리면 수십 초가 걸린다.
     * <p>
     * {@code @Async} 의 void 메서드는 예외가 조용히 사라지므로 여기서 반드시 잡아 남긴다.
     */
    @Async(ReportAsyncConfig.REPORT_EXECUTOR)
    public void generateAsync(Long reportId) {
        try {
            generate(reportId);
        } catch (RuntimeException e) {
            log.error("리포트 비동기 생성 실패: reportId={}", reportId, e);
        }
    }

    public ReportGenerationResult generate(Long reportId) {
        GenerationTarget target = loadTarget(reportId);
        if (target.members().isEmpty()) {
            // 활성 멤버가 없으면 서술할 대상이 없다. 계속 GENERATING 으로 두면 배치가 매일 다시 집는다.
            log.warn("활성 멤버가 없어 리포트를 실패 처리합니다: reportId={}", reportId);
            textWriter.markFailed(reportId);
            return new ReportGenerationResult(reportId, 0, 0, false);
        }

        List<BigDecimal> finalScores = new ArrayList<>();
        List<String> headlines = new ArrayList<>();
        double completionRateSum = 0.0;
        double complianceRateSum = 0.0;
        boolean externalConnected = false;
        int succeeded = 0;

        for (ProjectMember member : target.members()) {
            ReportMemberDataCollector.CollectedMember collected;
            try {
                collected = dataCollector.collect(
                        reportId, target.projectId(), target.projectType(), member, target.members().size());
            } catch (RuntimeException e) {
                // 점수 수집 실패는 텍스트 실패보다 무겁지만, 여기서도 멤버 단위로 격리한다.
                log.error("리포트 멤버 데이터 수집 실패: reportId={}, projectMemberId={}",
                        reportId, member.getId(), e);
                continue;
            }

            finalScores.add(collected.finalScore());
            completionRateSum += collected.internal().completionRate();
            complianceRateSum += collected.internal().deadlineComplianceRate();
            externalConnected |= collected.llmInput().externalToolConnected();

            try {
                // 트랜잭션 밖. 여기서 수 초~수십 초가 걸린다.
                ReportLlmGateway.GeneratedMemberText generated =
                        llmGateway.generateMemberText(collected.llmInput());
                textWriter.writeMemberText(reportId, collected.projectMemberId(), generated);
                headlines.add(generated.text().headline());
                succeeded++;
            } catch (RuntimeException e) {
                // 텍스트만 비고 점수는 남는다 — 이 멤버 카드는 점수만 나온다.
                log.error("리포트 멤버 텍스트 생성 실패: reportId={}, projectMemberId={}",
                        reportId, collected.projectMemberId(), e);
            }
        }

        if (succeeded == 0) {
            log.error("전원 텍스트 생성에 실패해 리포트를 실패 처리합니다: reportId={}", reportId);
            textWriter.markFailed(reportId);
            return new ReportGenerationResult(reportId, target.members().size(), 0, false);
        }

        writeTeamInsight(reportId, target, finalScores, headlines,
                completionRateSum, complianceRateSum, externalConnected);

        textWriter.publish(reportId);
        eventPublisher.publishEvent(new ReportPublishedEvent(target.projectId(), reportId));
        log.info("리포트 발행 완료: reportId={}, 멤버 {}명 중 {}명 텍스트 생성",
                reportId, target.members().size(), succeeded);
        return new ReportGenerationResult(reportId, target.members().size(), succeeded, true);
    }

    /**
     * 팀 인사이트는 있으면 좋은 것이라 실패해도 발행을 막지 않는다 —
     * 멤버별 결과가 리포트의 본체다.
     */
    private void writeTeamInsight(
            Long reportId,
            GenerationTarget target,
            List<BigDecimal> finalScores,
            List<String> headlines,
            double completionRateSum,
            double complianceRateSum,
            boolean externalConnected
    ) {
        int memberCount = target.members().size();
        try {
            TeamReportText teamText = llmGateway.generateTeamText(new TeamLlmInput(
                    target.projectType(),
                    memberCount,
                    completionRateSum / memberCount,
                    complianceRateSum / memberCount,
                    finalScores,
                    headlines,
                    externalConnected
            ));
            textWriter.writeTeamInsight(reportId, teamText);
        } catch (RuntimeException e) {
            log.error("팀 인사이트 생성 실패(리포트는 계속 발행합니다): reportId={}", reportId, e);
        }
    }

    /**
     * 대상 조회. 여기에 {@code @Transactional} 을 걸지 않는 이유는 두 가지다 —
     * 같은 빈에서 부르면 프록시를 안 타서 어차피 적용되지 않고, 트랜잭션이 열려 있으면
     * 뒤이은 긴 LLM 호출 동안 커넥션을 잡고 있게 된다. 리포지토리 호출은 각자 짧게 끝난다.
     * <p>
     * 그래서 이 메서드 밖으로 나가는 값은 LAZY 접근이 필요 없는 것만 담는다.
     */
    private GenerationTarget loadTarget(Long reportId) {
        Report report = reportRepository.findWithProjectById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.GENERATING) {
            // 이미 발행됐거나 실패한 리포트를 다시 생성하면 발행된 내용이 사후에 바뀐다.
            throw new ApiException(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        }
        Project project = report.getProject();
        List<ProjectMember> members = projectMemberRepository
                .findAllByProjectIdAndStatusOrderByIdAsc(project.getId(), MemberStatus.ACTIVE);
        return new GenerationTarget(project.getId(), project.getProjectType(), members);
    }

    private record GenerationTarget(
            Long projectId,
            com.plog.domain.project.entity.ProjectType projectType,
            List<ProjectMember> members
    ) {
    }
}
