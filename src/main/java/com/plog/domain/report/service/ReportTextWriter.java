package com.plog.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.notification.event.ReportPublishedEvent;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.llm.MemberReportText;
import com.plog.domain.report.llm.ReportLlmGateway;
import com.plog.domain.report.llm.TeamReportText;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.ai.LlmGenerationException;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LLM 이 만든 텍스트를 저장하는 짧은 트랜잭션들. LLM 호출 자체는 여기 들어오지 않는다 —
 * 수 초~수십 초짜리 호출을 트랜잭션 안에 두면 커넥션을 그만큼 붙잡는다.
 */
@Service
@RequiredArgsConstructor
public class ReportTextWriter {

    private final ReportRepository reportRepository;
    private final ReportMemberResultRepository memberResultRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 멤버 1명의 LLM 텍스트 저장. 재실행이면 덮어쓴다. */
    @Transactional
    public void writeMemberText(Long reportId, Long projectMemberId, ReportLlmGateway.GeneratedMemberText generated) {
        ReportMemberResult result = memberResultRepository
                .findByReportIdAndProjectMemberId(reportId, projectMemberId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_MEMBER_RESULT_NOT_FOUND));
        MemberReportText text = generated.text();
        result.applyLlmText(new ReportMemberResult.LlmTextPayload(
                text.headline(),
                text.teamMemberHeadline(),
                toJson(text.strengths()),
                toJson(text.weakness()),
                toJson(text.growth()),
                toJson(text.writing()),
                generated.rawResponse(),
                generated.model()
        ));
        memberResultRepository.save(result);
    }

    @Transactional
    public void writeTeamInsight(Long reportId, TeamReportText teamText) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        report.applyTeamInsight(teamText.strength(), teamText.suggestion());
    }

    /** 발행. 상태 전이 규칙은 엔티티가 강제하므로 여기서는 시각만 정해 넘긴다. */
    @Transactional
    public void publish(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        report.complete(TimeUtil.nowUtc());
        eventPublisher.publishEvent(new ReportPublishedEvent(report.getProject().getId(), reportId));
    }

    @Transactional
    public void markFailed(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        report.fail();
    }

    @Transactional
    public void attachPdfArchive(Long reportId, String objectKey, String fileName) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        report.attachPdf(objectKey, fileName);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 여기서 실패하면 이 멤버의 텍스트만 못 남기고 나머지는 그대로 간다.
            throw new LlmGenerationException("리포트 텍스트 직렬화에 실패했습니다.", e);
        }
    }
}
