package com.plog.domain.report.service;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.llm.MemberLlmInput;
import com.plog.domain.report.port.EvaluationSummaryProvider;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.InternalReportDataProvider;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.ReportRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4개 포트에서 멤버 1명분 데이터를 모아 점수를 확정하고 LLM 입력을 만든다.
 * <p>
 * 여기까지가 DB 작업이고, 이 뒤의 LLM 호출은 트랜잭션 밖에서 일어난다 —
 * 그래서 조립·점수저장과 텍스트저장을 별도 트랜잭션으로 나눠 둔다.
 */
@Service
@RequiredArgsConstructor
public class ReportMemberDataCollector {

    private final InternalReportDataProvider internalProvider;
    private final EvaluationSummaryProvider evaluationProvider;
    private final ReportMemberScoreService memberScoreService;
    private final ReportRepository reportRepository;

    /**
     * 포트 호출 → 점수 계산·저장 → LLM 입력 조립. 트랜잭션 1개다.
     * <p>
     * 점수를 LLM 호출 전에 확정하는 것이 중요하다. LLM 은 점수를 <b>서술만</b> 하므로
     * 서술 대상이 먼저 있어야 하고, LLM 이 실패해도 점수는 남아야 한다
     * (텍스트 없는 리포트는 반쪽이라도 쓸 수 있지만, 점수 없는 리포트는 아무 의미가 없다).
     */
    @Transactional
    public CollectedMember collect(
            Long reportId,
            Long projectId,
            ProjectType projectType,
            ProjectMember member,
            ExternalReportData external,
            int teamSize
    ) {
        Report report = reportRepository.getReferenceById(reportId);
        Long memberId = member.getId();

        InternalReportData internal = internalProvider.provide(projectId, memberId);
        PeerEvaluationSummary peer = evaluationProvider.peer(projectId, memberId);
        SelfFeedbackMatchSummary self = evaluationProvider.self(projectId, memberId);

        ReportMemberResult result = memberScoreService.calculateAndSave(
                report,
                member,
                new MemberScoreInput(
                        internal.internalScore(),
                        external.externalScore(),
                        peer.hasEvaluation() ? peer.average().multiply(new BigDecimal("20")) : null,
                        self.submitted() && self.matchRatio() != null ? self.normalizedScore() : null,
                        external.externalToolConnected(),
                        external.externalScore() != null,
                        external.reliabilityTier(),
                        external.cautionText()
                )
        );

        // result 는 memberScoreService.calculateAndSave 가 이미 저장(관리 상태)한 엔티티다.
        // 같은 트랜잭션 안이라 여기서 필드만 채워도 커밋 시점에 더티체킹으로 반영된다
        // (TaskStatusService.changeStatus 등 기존 코드베이스와 같은 패턴).
        result.applyTaskStatistics(
                internal.totalTaskCount(),
                internal.completedTaskCount(),
                internal.deadlineMetTaskCount(),
                internal.deadlineTargetTaskCount(),
                internal.completionRate(),
                internal.deadlineComplianceRate());

        // 팀 리포트 시안의 역량 점수/태그 표시용 Peer 집계(5점 척도). 점수와 같은 트랜잭션에서 더티체킹으로 반영된다.
        // 받은 평가가 없는 멤버(none())도 리포트에 나와야 하므로 null/빈 값을 그대로 저장한다.
        result.applyPeerBreakdown(peer.average(), peer.categoryScores(), peer.keywords());

        // projectType 을 member 에서 읽지 않는다 — member 는 오케스트레이션에서 트랜잭션 밖으로
        // 들고 나온 엔티티라 project 가 초기화되지 않은 프록시다(EntityGraph 는 user 만 채운다).
        MemberLlmInput llmInput = MemberLlmInput.of(
                projectType,
                teamSize,
                internal,
                external,
                peer,
                self,
                result.getFinalScore()
        );
        return new CollectedMember(memberId, llmInput, result.getFinalScore(), internal);
    }

    /**
     * @param finalScore 팀 인사이트의 점수 분포에 다시 쓰인다
     * @param internal   팀 KPI(완료율·마감준수율) 집계에 다시 쓰인다
     */
    public record CollectedMember(
            Long projectMemberId,
            MemberLlmInput llmInput,
            BigDecimal finalScore,
            InternalReportData internal
    ) {
    }
}
