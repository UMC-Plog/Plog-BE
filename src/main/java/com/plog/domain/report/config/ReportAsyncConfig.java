package com.plog.domain.report.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 리포트 생성 전용 스레드풀.
 * <p>
 * 공용 {@code applicationTaskExecutor} 를 쓰면 안 된다 — 리포트 1건은 멤버 수만큼 LLM 을
 * 순차 호출해서 수 분이 걸리는데, 같은 풀을 채팅 브로드캐스트와 알림 발송이 쓰고 있다.
 * 리포트 몇 건이 풀을 물면 채팅이 그만큼 늦는다.
 * <p>
 * 풀이 작은 것도 의도다. 리포트 생성은 급하지 않고(사용자는 폴링으로 기다린다),
 * 동시에 많이 돌리면 LLM 프로바이더의 분당 요청 제한에 먼저 걸린다.
 * 큐가 가득 차면 호출한 스레드에서 실행돼(CallerRuns) 요청이 조용히 버려지지 않는다.
 */
@Configuration
public class ReportAsyncConfig {

    public static final String REPORT_EXECUTOR = "reportTaskExecutor";

    @Bean(name = REPORT_EXECUTOR)
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("report-gen-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 시 진행 중인 리포트를 끊지 않는다 — 중간에 끊기면 GENERATING 으로 남는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
