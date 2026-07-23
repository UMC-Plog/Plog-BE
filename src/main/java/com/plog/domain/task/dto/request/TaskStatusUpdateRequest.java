package com.plog.domain.task.dto.request;

import com.plog.domain.task.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(
        @NotNull(message = "변경할 상태는 필수입니다.")
        TaskStatus cardStatus
) {
}