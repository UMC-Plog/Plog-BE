package com.plog.domain.report.port.fake;

import com.plog.domain.report.port.EvaluationSummaryProvider;
import com.plog.domain.report.port.InternalReportDataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 아직 실제 구현이 없는 포트만 더미로 채운다.
 * <p>
 * {@code @ConditionalOnMissingBean} 은 <b>{@code @Bean} 메서드에서만</b> 신뢰할 수 있다.
 * Fake 클래스에 직접 {@code @Component} 를 붙이면 컴포넌트 스캔 순서가 보장되지 않아
 * "실제 구현이 있는데도 더미가 함께 등록되는" 상황이 생긴다 — 그래서 등록을 여기로 모았다.
 * <p>
 * 담당자는 자기 포트의 구현체에 {@code @Component}(또는 {@code @Service}) 만 붙이면 된다.
 * 그러면 해당 {@code @Bean} 이 자동으로 물러나고, 나머지 포트의 더미는 그대로 남는다.
 * 세 포트가 모두 실제 구현으로 바뀌면 이 클래스와 {@code fake} 패키지를 통째로 지운다.
 */
@Configuration
public class ReportPortFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(InternalReportDataProvider.class)
    public InternalReportDataProvider fakeInternalReportDataProvider() {
        return new FakeInternalReportDataProvider();
    }

    @Bean
    @ConditionalOnMissingBean(EvaluationSummaryProvider.class)
    public EvaluationSummaryProvider fakeEvaluationSummaryProvider() {
        return new FakeEvaluationSummaryProvider();
    }
}
