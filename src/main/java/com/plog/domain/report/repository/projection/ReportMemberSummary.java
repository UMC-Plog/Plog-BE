package com.plog.domain.report.repository.projection;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.user.entity.ProfilePreset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 리포트 상세의 멤버 목록 한 줄. 엔티티를 로딩하면 멤버 수만큼 projectMember/user 를 타고 들어가
 * N+1 이 되므로 필요한 컬럼만 뽑는다. 상세 텍스트는 멤버별 결과 API 에서 따로 내려간다.
 */
public interface ReportMemberSummary {

    Long getProjectMemberId();

    /** 표시 닉네임 — anNickname 우선, 비어 있으면 user.nickname (ProjectMember.getDisplayNickname 과 같은 기준). */
    String getMemberName();

    BigDecimal getFinalScore();

    BigDecimal getContributionRate();

    ReliabilityTier getReliabilityTier();

    ProfilePreset getProfilePreset();

    int getTotalTaskCount();

    int getCompletedTaskCount();

    int getDeadlineMetTaskCount();

    int getDeadlineTargetTaskCount();

    BigDecimal getCompletionRate();

    BigDecimal getDeadlineComplianceRate();

    BigDecimal getPeerAverage();

    Map<CompetencyCategory, BigDecimal> getCompetencyScores();

    List<String> getPeerKeywords();

    /** 팀 리포트 멤버 카드 전용 AI 활동 요약. */
    String getHeadline();
}
