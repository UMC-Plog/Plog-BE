package com.plog.domain.task.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.plog.global.util.TimeUtil;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void stampsCompletionTimeOnTheStorageZoneWhenCreatedAsDone() {
        Task task = Task.create(null, "완료된 카드", null, TaskStatus.DONE, null);

        assertThat(task.getCompletedAt())
                .isCloseTo(TimeUtil.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void leavesCompletionTimeEmptyWhenNotDone() {
        Task task = Task.create(null, "진행중 카드", null, TaskStatus.IN_PROGRESS, null);

        assertThat(task.getCompletedAt()).isNull();
    }
}
