package com.plog.domain.report.entity;

/** 2단계 분류 결과. 내부 도메인(TASK/CHAT/POST) 활동에만 값이 채워지고, 외부 활동은 항상 null이다. */
public enum ActivityCategory {
    DECISION,               // 의사결정
    PROBLEM_SOLVING,        // 문제해결
    FEEDBACK,                // 피드백
    DELIVERABLE_SUBMIT,     // 산출물제출
    SCHEDULE_COORDINATION,  // 일정조율
    SIMPLE_RESPONSE          // 단순응답
}