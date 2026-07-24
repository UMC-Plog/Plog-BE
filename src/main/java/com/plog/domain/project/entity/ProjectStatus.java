package com.plog.domain.project.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 진행 상태. IN_PROGRESS=진행 중, COMPLETED=완료",
        allowableValues = {"IN_PROGRESS", "COMPLETED"})
public enum ProjectStatus {
    IN_PROGRESS, COMPLETED
}
