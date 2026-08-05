package com.plog.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class E2eAsyncConfigurationTest extends E2eTestBase {

    @Autowired
    private E2eTaskExecutor taskExecutor;

    @Test
    void E2E에서는_단일_비동기_큐를_사용하고_대기할_수_있다() {
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(1);
        taskExecutor.awaitIdle();
    }
}
