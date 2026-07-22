package com.plog.domain.task.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void stampsCompletionTimeInUtcWhenCreatedAsDone() {
        Task task = Task.create(null, "완료된 카드", null, TaskStatus.DONE, null);

        assertThat(task.getCompletedAt())
                .isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void leavesCompletionTimeEmptyWhenNotDone() {
        Task task = Task.create(null, "진행중 카드", null, TaskStatus.IN_PROGRESS, null);

        assertThat(task.getCompletedAt()).isNull();
    }
}
