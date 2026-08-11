package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.global.util.TimeUtil;
import java.time.Instant;

public record TaskStatusUpdateResponse(
        Long taskId,
        TaskStatus cardStatus,
        Instant completedAt // DONE이 아니면 null
) {
    public static TaskStatusUpdateResponse from(Task task) {
        return new TaskStatusUpdateResponse(
                task.getId(),
                task.getCardStatus(),
                TimeUtil.toInstant(task.getCompletedAt())
        );
    }
}
