package com.plog.domain.report.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemberLlmInputTest {

    @Test
    void 외부_점수_가능_여부와_역량별_활동_건수를_LLM_입력에_포함한다() {
        InternalReportData internal = new InternalReportData(
                List.of(),
                0,
                0,
                0.0,
                0.0,
                List.of("산출물 1건"),
                Map.of(ActivityCategory.DELIVERABLE_SUBMIT, 2),
                Map.of(CompetencyCategory.OUTPUT, List.of("Plog: 산출물 첨부")),
                new BigDecimal("80")
        );
        ExternalReportData external = ExternalReportData.connectedWithoutScore(
                Map.of(SourceDomain.GITHUB, 2L),
                Map.of(CompetencyCategory.COMMUNICATION, 2L),
                Map.of(CompetencyCategory.COMMUNICATION, List.of("GitHub: 리뷰 댓글 2건")),
                ReliabilityTier.P2,
                "점수화 가능한 외부 활동이 부족해요"
        );

        MemberLlmInput input = MemberLlmInput.of(
                ProjectType.DEVELOP,
                4,
                internal,
                external,
                new PeerEvaluationSummary(
                        new BigDecimal("4.0"),
                        Map.of(CompetencyCategory.COLLABORATION, new BigDecimal("4.2")),
                        2,
                        List.of("책임감"),
                        new BigDecimal("75")
                ),
                new SelfFeedbackMatchSummary(true, 3, 1, 0, new BigDecimal("0.75"), new BigDecimal("70")),
                new BigDecimal("76.50")
        );

        assertThat(input.externalToolConnected()).isTrue();
        assertThat(input.externalScoreAvailable()).isFalse();
        assertThat(input.externalActivityCountByDomain()).containsEntry(SourceDomain.GITHUB.name(), 2L);
        assertThat(input.externalCompetencyActivityCount())
                .containsEntry(CompetencyCategory.COMMUNICATION.name(), 2L)
                .containsEntry(CompetencyCategory.OUTPUT.name(), 0L);
        assertThat(input.competencyEvidence().get(CompetencyCategory.OUTPUT.name()))
                .containsExactly("Plog: 산출물 첨부");
        assertThat(input.competencyEvidence().get(CompetencyCategory.COMMUNICATION.name()))
                .containsExactly("GitHub: 리뷰 댓글 2건");
    }
}
