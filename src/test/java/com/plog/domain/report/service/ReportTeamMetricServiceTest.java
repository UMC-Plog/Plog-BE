package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportTeamMetricServiceTest {

    @Mock private ReportMemberResultRepository resultRepository;
    @Mock private ReportRepository reportRepository;
    private ReportTeamMetricService service;
    private Report report;

    @BeforeEach
    void setUp() {
        service = new ReportTeamMetricService(resultRepository, reportRepository);
        report = org.mockito.Mockito.mock(Report.class);
        when(reportRepository.findById(20L)).thenReturn(Optional.of(report));
    }

    @Test
    void 기여율은_largest_remainder로_정확히_100이며_동점은_멤버ID로_결정한다() {
        List<ReportMemberResult> results = List.of(
                result(1L, "1", null), result(2L, "1", null), result(3L, "1", null));
        stub(results);

        service.calculateAndApply(20L, 3);

        assertThat(results).extracting(ReportMemberResult::getContributionRate)
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(results.stream().map(ReportMemberResult::getContributionRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test
    void 모든_종합점수가_0이거나_null이면_기여율을_만들지_않는다() {
        List<ReportMemberResult> zero = List.of(result(1L, "0", null), result(2L, "0", null));
        stub(zero);
        service.calculateAndApply(20L, 2);
        assertThat(zero).extracting(ReportMemberResult::getContributionRate).containsOnlyNulls();

        List<ReportMemberResult> unavailable = List.of(result(1L, null, null), result(2L, null, null));
        stub(unavailable);
        service.calculateAndApply(20L, 2);
        assertThat(unavailable).extracting(ReportMemberResult::getContributionRate).containsOnlyNulls();
    }

    @Test
    void 일부_멤버_점수가_null이면_불완전한_기여율을_발행하지_않는다() {
        List<ReportMemberResult> results = List.of(result(1L, "80", null), result(2L, null, null));
        stub(results);

        service.calculateAndApply(20L, 2);

        assertThat(results).extracting(ReportMemberResult::getContributionRate).containsOnlyNulls();
    }

    @Test
    void 협업안정도는_고정가중치로_계산하고_Peer가_없으면_null이다() {
        ReportMemberResult complete = result(1L, "80", scores("4", "3", "5", "4"));
        complete.applyTaskStatistics(2, 2, 1, 1.0, 0.8);
        ReportMemberResult noPeer = result(2L, "70", null);
        noPeer.applyTaskStatistics(2, 1, 1, 0.5, 0.8);
        stub(List.of(complete, noPeer));

        service.calculateAndApply(20L, 2);

        // deadline 80*0.4 + collaboration 80*0.3 + communication 100*0.3
        assertThat(complete.getCollaborationStability()).isEqualByComparingTo("86.00");
        assertThat(noPeer.getCollaborationStability()).isNull();
    }

    @Test
    void 역량_percentile의_최저축으로_개선필요도를_계산한다() {
        ReportMemberResult first = result(1L, "80", scores("1", "5", "5", "5"));
        ReportMemberResult middle = result(2L, "80", scores("3", "3", "3", "3"));
        ReportMemberResult last = result(3L, "80", scores("5", "1", "1", "1"));
        stub(List.of(first, middle, last));

        service.calculateAndApply(20L, 3);

        assertThat(first.getVulnerableCompetency()).isEqualTo(CompetencyCategory.COLLABORATION);
        assertThat(first.getVulnerability()).isNotNull();
        assertThat(first.getPeerZScore()).isNotNull();
        assertThat(first.getPeerPercentile()).isNotNull();
        assertThat(first.getPeerScore()).isEqualByComparingTo("80");
        assertThat(last.getVulnerableCompetency()).isEqualTo(CompetencyCategory.LEADERSHIP);
    }

    @Test
    void 팀원_1명이나_모든_역량점수가_같으면_상대지표와_취약축은_null이다() {
        ReportMemberResult only = result(1L, "80", scores("3", "3", "3", "3"));
        stub(List.of(only));
        service.calculateAndApply(20L, 1);
        assertThat(only.getPeerZScore()).isNull();
        assertThat(only.getPeerPercentile()).isNull();
        assertThat(only.getVulnerability()).isNull();
        assertThat(only.getVulnerableCompetency()).isNull();

        ReportMemberResult sameA = result(1L, "80", scores("3", "3", "3", "3"));
        ReportMemberResult sameB = result(2L, "90", scores("3", "3", "3", "3"));
        stub(List.of(sameA, sameB));
        service.calculateAndApply(20L, 2);
        assertThat(sameA.getVulnerability()).isNull();
        assertThat(sameB.getVulnerability()).isNull();
    }

    @Test
    void 팀_비율은_계산가능한_멤버별_비율의_단순평균이다() {
        ReportMemberResult first = result(1L, "80", null);
        first.applyTaskStatistics(4, 2, 1, 0.5, 0.8);
        ReportMemberResult second = result(2L, "90", null);
        second.applyTaskStatistics(0, 0, 0, null, null);
        ReportMemberResult third = result(3L, "70", null);
        third.applyTaskStatistics(2, 2, 0, 1.0, null);
        stub(List.of(first, second, third));

        service.calculateAndApply(20L, 3);

        org.mockito.Mockito.verify(report).applyTeamRates(
                new BigDecimal("75.00"), new BigDecimal("80.00"));
    }

    private void stub(List<ReportMemberResult> results) {
        when(resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(20L)).thenReturn(results);
    }

    private ReportMemberResult result(Long memberId, String finalScore, Map<CompetencyCategory, BigDecimal> scores) {
        ProjectMember member = ProjectMember.builder().id(memberId).build();
        ReportMemberResult result = ReportMemberResult.create(report, member);
        BigDecimal score = finalScore == null ? null : new BigDecimal(finalScore);
        result.applyScores(score, null, score, null, score, false, ReliabilityTier.P0, null);
        if (scores != null) {
            BigDecimal average = scores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()));
            result.applyPeerBreakdown(average, scores, List.of());
        }
        return result;
    }

    private Map<CompetencyCategory, BigDecimal> scores(
            String collaboration, String leadership, String communication, String output
    ) {
        Map<CompetencyCategory, BigDecimal> scores = new EnumMap<>(CompetencyCategory.class);
        scores.put(CompetencyCategory.COLLABORATION, new BigDecimal(collaboration));
        scores.put(CompetencyCategory.LEADERSHIP, new BigDecimal(leadership));
        scores.put(CompetencyCategory.COMMUNICATION, new BigDecimal(communication));
        scores.put(CompetencyCategory.OUTPUT, new BigDecimal(output));
        return scores;
    }
}
