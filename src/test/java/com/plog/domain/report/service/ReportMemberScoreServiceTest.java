package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportMemberScoreServiceTest {
    @Mock private ReportMemberResultRepository resultRepository;

    private ReportMemberScoreService service;

    @BeforeEach
    void setUp() {
        service = new ReportMemberScoreService(resultRepository);
    }

    @Test
    void 외부_연동_점수는_35_15_35_15로_계산해_저장한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());

        ReportMemberResult result = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("70"),
                new BigDecimal("60"), true, ReliabilityTier.P1, "일부 활동이 제외될 수 있어요"));

        assertThat(result.getFinalScore()).isEqualByComparingTo("75.00");
        assertThat(result.getExternalScore()).isEqualByComparingTo("90.00");
        assertThat(result.isExternalToolConnected()).isTrue();
        verify(resultRepository).save(result);
    }

    @Test
    void 외부_미연동이면_나머지_가중치를_비례_재분배한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());

        ReportMemberResult result = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("80"), null, new BigDecimal("70"), new BigDecimal("60"),
                false, ReliabilityTier.P2, "외부 도구가 연결되지 않았어요"));

        assertThat(result.getFinalScore()).isEqualByComparingTo("72.35");
        assertThat(result.getExternalScore()).isNull();
    }

    @Test
    void 필수_점수_누락이나_범위_초과는_거부한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();

        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                null, null, BigDecimal.TEN, BigDecimal.TEN, false, ReliabilityTier.P3, "제한")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.TEN, new BigDecimal("101"), BigDecimal.TEN, BigDecimal.TEN,
                true, ReliabilityTier.P0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.TEN, null, BigDecimal.TEN, BigDecimal.TEN,
                true, ReliabilityTier.P0, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 점수_경계값_0과_100을_허용한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ReportMemberResult.create(report, member)));

        ReportMemberResult zero = service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                true, ReliabilityTier.P0, null));
        ReportMemberResult hundred = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                true, ReliabilityTier.P0, null));

        assertThat(zero.getFinalScore()).isEqualByComparingTo("0.00");
        assertThat(hundred.getFinalScore()).isEqualByComparingTo("100.00");
    }
}
