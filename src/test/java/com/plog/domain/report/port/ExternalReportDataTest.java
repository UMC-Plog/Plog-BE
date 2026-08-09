package com.plog.domain.report.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.SourceDomain;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalReportDataTest {

    @Test
    void 점수_가능_상태는_연동과_점수를_함께_요구한다() {
        assertThatThrownBy(() -> new ExternalReportData(
                false, true, Map.of(), Map.of(), Map.of(),
                BigDecimal.TEN, ReliabilityTier.P0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalReportData(
                true, true, Map.of(), Map.of(), Map.of(),
                null, ReliabilityTier.P0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalReportData(
                true, false, Map.of(), Map.of(), Map.of(),
                BigDecimal.TEN, ReliabilityTier.P0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExternalReportData(
                true, true, Map.of(), Map.of(), Map.of(),
                new BigDecimal("101"), ReliabilityTier.P0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 역량별_활동_건수는_모든_역량_키를_0_기본값으로_채운다() {
        ExternalReportData data = ExternalReportData.connectedWithoutScore(
                Map.of(SourceDomain.GITHUB, 3L),
                Map.of(CompetencyCategory.OUTPUT, 2L),
                Map.of(),
                ReliabilityTier.P2,
                "점수화 가능한 활동이 부족해요"
        );

        assertThat(data.externalToolConnected()).isTrue();
        assertThat(data.externalScoreAvailable()).isFalse();
        assertThat(data.externalScore()).isNull();
        assertThat(data.competencyActivityCount())
                .containsEntry(CompetencyCategory.OUTPUT, 2L)
                .containsEntry(CompetencyCategory.COLLABORATION, 0L)
                .containsEntry(CompetencyCategory.LEADERSHIP, 0L)
                .containsEntry(CompetencyCategory.COMMUNICATION, 0L);
    }

    @Test
    void 근거_목록은_깊은_복사로_보호한다() {
        List<String> outputEvidence = new ArrayList<>(List.of("GitHub: PR 병합"));
        Map<CompetencyCategory, List<String>> evidence = new EnumMap<>(CompetencyCategory.class);
        evidence.put(CompetencyCategory.OUTPUT, outputEvidence);

        ExternalReportData data = new ExternalReportData(
                true,
                true,
                Map.of(SourceDomain.GITHUB, 1L),
                Map.of(CompetencyCategory.OUTPUT, 1L),
                evidence,
                new BigDecimal("80"),
                ReliabilityTier.P2,
                null
        );
        outputEvidence.add("GitHub: 뒤늦은 수정");

        assertThat(data.competencyEvidence().get(CompetencyCategory.OUTPUT))
                .containsExactly("GitHub: PR 병합");
        assertThatThrownBy(() -> data.competencyEvidence().get(CompetencyCategory.OUTPUT).add("변경"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
