package com.plog.domain.report.entity;

/** 2단계 분류 결과. 내부 도메인(TASK/CHAT/POST) 활동에만 값이 채워지고, 외부 활동은 항상 null이다. */
public enum ActivityCategory {
    DECISION,               // 의사결정
    PROBLEM_SOLVING,        // 문제해결
    FEEDBACK,                // 피드백
    DELIVERABLE_SUBMIT,     // 산출물제출
    SCHEDULE_COORDINATION,  // 일정조율
    SIMPLE_RESPONSE;          // 단순응답

    /**
     * 이 활동 유형이 어느 역량 축({@link CompetencyCategory})의 기여 근거로 잡히는지.
     * {@link #SIMPLE_RESPONSE}는 "성의 없는 확인 응답"이라 어떤 역량에도 기여로 반영하지 않는다(null).
     */
    public CompetencyCategory competencyCategory() {
        return switch (this) {
            case DECISION -> CompetencyCategory.LEADERSHIP;
            case PROBLEM_SOLVING -> CompetencyCategory.OUTPUT;
            case FEEDBACK -> CompetencyCategory.COMMUNICATION;
            case DELIVERABLE_SUBMIT -> CompetencyCategory.OUTPUT;
            case SCHEDULE_COORDINATION -> CompetencyCategory.COLLABORATION;
            case SIMPLE_RESPONSE -> null;
        };
    }
}