package com.plog.domain.task.entity;

import com.plog.domain.project.entity.ProjectType;

// ERD: 기획, 디자인, 개발, 테스트 수정, 발표 문서, 기타
public enum TaskCategory {
    // 개발 프로젝트 담당 영역
    PLANNING(ProjectType.DEVELOP),
    DESIGN(ProjectType.DEVELOP),
    DEVELOP(ProjectType.DEVELOP),
    TEST_FIX(ProjectType.DEVELOP),
    PRESENTATION_DOC(ProjectType.DEVELOP),

    // 일반 팀프로젝트 담당 영역
    RESEARCH(ProjectType.GENERAL),
    MATERIAL_PRODUCTION(ProjectType.GENERAL),
    PRESENTATION(ProjectType.GENERAL),
    SCHEDULE_MANAGEMENT(ProjectType.GENERAL),

    // 공통 — 두 유형 모두 선택 가능
    ETC(null);

    private final ProjectType projectType;

    TaskCategory(ProjectType projectType) {
        this.projectType = projectType;
    }

    public boolean isAllowedFor(ProjectType type) {
        return this.projectType == null || this.projectType == type;
    }
}
