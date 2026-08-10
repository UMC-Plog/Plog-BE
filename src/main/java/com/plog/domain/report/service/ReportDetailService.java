package com.plog.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.llm.MemberReportText;
import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import com.plog.domain.report.dto.response.ReportMemberSummaryResponse;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.report.repository.projection.ReportMemberSummary;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportDetailService {

    private static final TypeReference<List<MemberReportText.StrengthCard>> STRENGTH_LIST =
            new TypeReference<>() {
            };

    private final ReportRepository reportRepository;
    private final ReportMemberResultRepository memberResultRepository;
    private final ProjectAccessService projectAccessService;
    private final ObjectMapper objectMapper;

    /**
     * 리포트 1건의 상태와 멤버 요약. 발행 전(GENERATING/FAILED)에도 404 가 아니라 200 으로
     * 상태를 내려준다 — 프론트가 "생성 중" 화면을 그리고 폴링할 수 있어야 하기 때문이다.
     * 이때 members 는 빈 배열이다(아직 채워졌다는 보장이 없다).
     */
    @Transactional(readOnly = true)
    public ReportDetailResponse getReport(Long userId, Long reportId) {
        Report report = requireAccessibleReport(userId, reportId);
        List<ReportMemberSummaryResponse> members = report.getStatus().isPublished()
                ? memberResultRepository.findMemberSummaries(reportId).stream()
                        .map(this::toMemberSummaryResponse)
                        .toList()
                : List.of();
        return toDetailResponse(report, members);
    }

    /**
     * 멤버 1명의 점수 상세. 발행 전에는 결과 행 자체가 없으므로 404 다 — 상태만 필요하면
     * 리포트 상세 API 를 쓴다.
     */
    @Transactional(readOnly = true)
    public ReportMemberResultResponse getMemberResult(Long userId, Long reportId, Long projectMemberId) {
        requireAccessibleReport(userId, reportId);
        ReportMemberResult result = memberResultRepository
                .findWithMemberByReportIdAndProjectMemberId(reportId, projectMemberId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_MEMBER_RESULT_NOT_FOUND));
        return toMemberResultResponse(reportId, result);
    }

    /**
     * 리포트를 찾고 "그 리포트가 속한 프로젝트의 ACTIVE 멤버인지"까지 확인한다.
     * 같은 프로젝트 멤버면 다른 멤버의 결과도 볼 수 있다 — 리포트가 팀 공용 산출물이라는 전제다.
     */
    private Report requireAccessibleReport(Long userId, Long reportId) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        Report report = reportRepository.findWithProjectById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        projectAccessService.requireActiveMember(report.getProject().getId(), userId);
        return report;
    }

    private ReportDetailResponse toDetailResponse(Report report, List<ReportMemberSummaryResponse> members) {
        return new ReportDetailResponse(
                report.getId(),
                report.getReportCode(),
                report.getProject().getId(),
                report.getProject().getProjectName(),
                report.getStatus(),
                TimeUtil.toInstant(report.getCompletedAt()),
                isPdfAvailable(report),
                report.getTeamStrength(),
                report.getTeamSuggestion(),
                report.getTeamCompletionRate(),
                report.getTeamDeadlineComplianceRate(),
                members
        );
    }

    // PDF 다운로드 API 가 통과시키는 조건과 같은 기준이어야 한다 — 프론트가 이 값만 보고 버튼을 그린다.
    private boolean isPdfAvailable(Report report) {
        return report.getStatus().isPublished()
                && report.getPdfObjectKey() != null && !report.getPdfObjectKey().isBlank()
                && report.getPdfFileName() != null && !report.getPdfFileName().isBlank();
    }

    private ReportMemberSummaryResponse toMemberSummaryResponse(ReportMemberSummary summary) {
        return new ReportMemberSummaryResponse(
                summary.getProjectMemberId(),
                summary.getMemberName(),
                summary.getFinalScore(),
                summary.getContributionRate(),
                summary.getReliabilityTier(),
                summary.getHeadline()
        );
    }

    private ReportMemberResultResponse toMemberResultResponse(Long reportId, ReportMemberResult result) {
        return new ReportMemberResultResponse(
                reportId,
                result.getProjectMember().getId(),
                result.getProjectMember().getDisplayNickname(),
                result.getInternalScore(),
                result.getExternalScore(),
                result.getPeerScore(),
                result.getPeerAverage(),
                result.getPeerZScore(),
                result.getPeerPercentile(),
                result.getCompetencyScores(),
                result.getPeerKeywords(),
                result.getSelfFeedbackScore(),
                result.getFinalScore(),
                result.getContributionRate(),
                result.isExternalToolConnected(),
                result.getReliabilityTier(),
                result.getCautionText(),
                result.getTotalTaskCount(),
                result.getCompletedTaskCount(),
                result.getDeadlineMetTaskCount(),
                result.getDeadlineTargetTaskCount(),
                result.getCompletionRate(),
                result.getDeadlineComplianceRate(),
                result.getCollaborationStability(),
                result.getVulnerability(),
                result.getVulnerableCompetency(),
                result.getHeadline(),
                readJson(result.getStrengths(), STRENGTH_LIST),
                readJson(result.getWeakness(), MemberReportText.Weakness.class),
                readJson(result.getGrowth(), MemberReportText.GrowthInsight.class),
                readJson(result.getWriting(), MemberReportText.WritingSuggestion.class)
        );
    }

    /**
     * jsonb 컬럼은 문자열로 들고 있으므로 응답에 그대로 실으면 이스케이프된 문자열이 나간다.
     * 프론트가 한 번 더 파싱하지 않도록 여기서 객체로 되돌린다.
     * <p>
     * 파싱이 깨져도 조회 전체를 실패시키지 않는다 — 저장된 텍스트가 손상된 경우라도
     * 점수와 나머지 섹션은 보여줄 수 있어야 한다.
     */
    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("리포트 텍스트 역직렬화 실패, 해당 섹션을 비웁니다: type={}", type.getSimpleName(), e);
            return null;
        }
    }

    private List<MemberReportText.StrengthCard> readJson(String json, TypeReference<List<MemberReportText.StrengthCard>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("리포트 강점 카드 역직렬화 실패, 빈 목록으로 대체합니다", e);
            return List.of();
        }
    }
}
