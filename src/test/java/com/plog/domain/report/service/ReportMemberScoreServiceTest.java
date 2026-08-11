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
    void 네_점수는_40_20_35_5로_계산해_저장한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());

        ReportMemberResult result = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("70"),
                new BigDecimal("60"), true, true, ReliabilityTier.P1, "일부 활동이 제외될 수 있어요"));

        assertThat(result.getFinalScore()).isEqualByComparingTo("77.50");
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
                false, false, ReliabilityTier.P2, "외부 도구가 연결되지 않았어요"));

        assertThat(result.getFinalScore()).isEqualByComparingTo("74.38");
        assertThat(result.getExternalScore()).isNull();
    }

    @Test
    void 외부_연동됐지만_점수화_불가하면_연동은_저장하고_나머지_가중치를_재분배한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());

        ReportMemberResult result = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("80"), null, new BigDecimal("70"), new BigDecimal("60"),
                true, false, ReliabilityTier.P2, "외부 활동은 있지만 점수화 가능한 로그가 부족해요"));

        assertThat(result.getFinalScore()).isEqualByComparingTo("74.38");
        assertThat(result.getExternalScore()).isNull();
        assertThat(result.isExternalToolConnected()).isTrue();
    }

    @Test
    void null_점수는_분모에서_제외하고_범위_초과는_거부한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();

        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());
        ReportMemberResult partial = service.calculateAndSave(report, member, new MemberScoreInput(
                null, null, BigDecimal.TEN, BigDecimal.TEN, false, false, ReliabilityTier.P3, "제한"));
        assertThat(partial.getFinalScore()).isEqualByComparingTo("10.00");
        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.TEN, new BigDecimal("101"), BigDecimal.TEN, BigDecimal.TEN,
                true, true, ReliabilityTier.P0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.TEN, null, BigDecimal.TEN, BigDecimal.TEN,
                true, true, ReliabilityTier.P0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                true, false, ReliabilityTier.P0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculateAndSave(report, member, new MemberScoreInput(
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                false, true, ReliabilityTier.P0, null)))
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
                true, true, ReliabilityTier.P0, null));
        ReportMemberResult hundred = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                true, true, ReliabilityTier.P0, null));

        assertThat(zero.getFinalScore()).isEqualByComparingTo("0.00");
        assertThat(hundred.getFinalScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void 내부와_Peer만_있으면_40대35_가중치를_비례_재분배한다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());

        ReportMemberResult result = service.calculateAndSave(report, member, new MemberScoreInput(
                new BigDecimal("80"), null, new BigDecimal("90"), null,
                false, false, ReliabilityTier.P0, null));

        assertThat(result.getFinalScore()).isEqualByComparingTo("84.67");
    }

    @Test
    void 모든_축이_null이면_finalScore도_null이고_실제_0점과_구분된다() {
        Report report = org.mockito.Mockito.mock(Report.class);
        ProjectMember member = ProjectMember.builder().id(2L).build();
        when(report.getId()).thenReturn(1L);
        when(resultRepository.findByReportIdAndProjectMemberId(1L, 2L)).thenReturn(Optional.empty());

        ReportMemberResult unavailable = service.calculateAndSave(report, member, new MemberScoreInput(
                null, null, null, null, false, false, ReliabilityTier.P0, null));

        assertThat(unavailable.getFinalScore()).isNull();
    }
}
