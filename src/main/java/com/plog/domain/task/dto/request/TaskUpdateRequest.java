package com.plog.domain.task.dto.request;

import com.plog.domain.task.entity.TaskCategory;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// PATCH 부분 수정 — 필드가 null이면 "변경 안 함"을 의미한다.
public record TaskUpdateRequest(
        @Size(min = 2, message = "업무명은 2자 이상이어야 합니다.")
        String title,

        Long projectMemberId,

        TaskCategory category,

        LocalDate endDate
) {
}