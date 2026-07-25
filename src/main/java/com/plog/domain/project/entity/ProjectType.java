package com.plog.domain.project.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 유형. DEVELOP=개발 프로젝트, GENERAL=일반 프로젝트",
        allowableValues = {"DEVELOP", "GENERAL"})
public enum ProjectType {
    DEVELOP,
    GENERAL // ERD의 "GENRAL"은 오타로 판단하여 GENERAL로 수정
}
