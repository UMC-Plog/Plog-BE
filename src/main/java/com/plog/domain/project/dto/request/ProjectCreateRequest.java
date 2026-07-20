package com.plog.domain.project.dto.request;

import com.plog.domain.project.entity.ProjectType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ProjectCreateRequest(
        String projectName,
        @NotNull(message = "프로젝트 유형은 필수입니다.") ProjectType projectType,
        LocalDate endDay
) {
}
