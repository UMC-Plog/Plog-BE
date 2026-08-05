package com.plog.domain.report.port.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.report.port.EvaluationSummaryProvider;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.ExternalReportDataProvider;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.InternalReportDataProvider;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이 테스트가 지키는 계약: <b>담당자가 구현체에 {@code @Component} 만 붙이면 더미가 물러난다.</b>
 * 팀원 4명이 각자 다른 시점에 머지하므로, 일부만 실제 구현으로 바뀐 중간 상태도 함께 검증한다.
 */
class ReportPortFallbackConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReportPortFallbackConfig.class));

    @Test
    void registersFakesWhenNoRealImplementationExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(InternalReportDataProvider.class);
            assertThat(context).hasSingleBean(ExternalReportDataProvider.class);
            assertThat(context).hasSingleBean(EvaluationSummaryProvider.class);
            assertThat(context.getBean(InternalReportDataProvider.class))
                    .isInstanceOf(FakeInternalReportDataProvider.class);
            assertThat(context.getBean(ExternalReportDataProvider.class))
                    .isInstanceOf(FakeExternalReportDataProvider.class);
            assertThat(context.getBean(EvaluationSummaryProvider.class))
                    .isInstanceOf(FakeEvaluationSummaryProvider.class);
        });
    }

    // 송민만 먼저 머지한 상태 — 내부 포트만 실제 구현이고 나머지 둘은 더미로 남아야 한다.
    @Test
    void backsOffOnlyForThePortThatGotARealImplementation() {
        contextRunner.withUserConfiguration(RealInternalOnlyConfig.class).run(context -> {
            assertThat(context).hasSingleBean(InternalReportDataProvider.class);
            assertThat(context.getBean(InternalReportDataProvider.class))
                    .isInstanceOf(RealInternalReportDataProvider.class);
            assertThat(context.getBean(ExternalReportDataProvider.class))
                    .isInstanceOf(FakeExternalReportDataProvider.class);
            assertThat(context.getBean(EvaluationSummaryProvider.class))
                    .isInstanceOf(FakeEvaluationSummaryProvider.class);
        });
    }

    @Test
    void registersNoFakeWhenEveryPortIsImplemented() {
        contextRunner.withUserConfiguration(AllRealConfig.class).run(context -> {
            assertThat(context).hasSingleBean(InternalReportDataProvider.class);
            assertThat(context).hasSingleBean(ExternalReportDataProvider.class);
            assertThat(context).hasSingleBean(EvaluationSummaryProvider.class);
            assertThat(context.getBeansOfType(InternalReportDataProvider.class).values())
                    .noneMatch(FakeInternalReportDataProvider.class::isInstance);
            assertThat(context.getBeansOfType(EvaluationSummaryProvider.class).values())
                    .noneMatch(FakeEvaluationSummaryProvider.class::isInstance);
        });
    }

    // 더미 값이 프롬프트 분기(미연동·미제출 경로)를 실제로 태우는지 — 정상 경로만 검증되면 의미가 없다.
    @Test
    void fakesCoverBothConnectedAndDisconnectedBranches() {
        FakeExternalReportDataProvider external = new FakeExternalReportDataProvider();
        FakeEvaluationSummaryProvider evaluation = new FakeEvaluationSummaryProvider();

        assertThat(external.provide(1L, 1L).externalToolConnected()).isTrue();
        assertThat(external.provide(1L, 2L).externalToolConnected()).isFalse();
        assertThat(evaluation.self(1L, 1L).submitted()).isTrue();
        assertThat(evaluation.self(1L, 3L).submitted()).isFalse();
    }

    // 5점 척도(화면)와 100점 척도(점수 조립)가 뒤섞이지 않았는지 더미 값에서 고정해 둔다.
    @Test
    void fakePeerSummaryKeepsFiveAndHundredPointScalesSeparate() {
        PeerEvaluationSummary peer = new FakeEvaluationSummaryProvider().peer(1L, 1L);

        assertThat(peer.average()).isEqualByComparingTo("4.25");
        assertThat(peer.categoryScores().values())
                .allSatisfy(score -> assertThat(score).isBetween(
                        java.math.BigDecimal.ZERO, new java.math.BigDecimal("5")));
        assertThat(peer.normalizedScore()).isEqualByComparingTo("80.00");
        assertThat(peer.hasEvaluation()).isTrue();
    }

    @Configuration
    static class RealInternalOnlyConfig {
        @Bean
        InternalReportDataProvider realInternalReportDataProvider() {
            return new RealInternalReportDataProvider();
        }
    }

    @Configuration
    static class AllRealConfig {
        @Bean
        InternalReportDataProvider realInternalReportDataProvider() {
            return new RealInternalReportDataProvider();
        }

        @Bean
        ExternalReportDataProvider realExternalReportDataProvider() {
            return (projectId, projectMemberId) -> ExternalReportData.notConnected();
        }

        @Bean
        EvaluationSummaryProvider realEvaluationSummaryProvider() {
            return new EvaluationSummaryProvider() {
                @Override
                public PeerEvaluationSummary peer(Long projectId, Long projectMemberId) {
                    return PeerEvaluationSummary.none();
                }

                @Override
                public SelfFeedbackMatchSummary self(Long projectId, Long projectMemberId) {
                    return SelfFeedbackMatchSummary.notSubmitted();
                }
            };
        }
    }

    static class RealInternalReportDataProvider implements InternalReportDataProvider {
        @Override
        public InternalReportData provide(Long projectId, Long projectMemberId) {
            return InternalReportData.empty();
        }
    }
}
