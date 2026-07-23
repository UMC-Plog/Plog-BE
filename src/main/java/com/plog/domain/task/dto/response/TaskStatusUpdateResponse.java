package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDateTime;

public record TaskStatusUpdateResponse(
        Long taskId,
        TaskStatus cardStatus,
        LocalDateTime completedAt // DONE이 아니면 null
) {
    public static TaskStatusUpdateResponse from(Task task) {
        return new TaskStatusUpdateResponse(
                task.getId(),
                task.getCardStatus(),
                task.getCompletedAt()
        );
    }
}