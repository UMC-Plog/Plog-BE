package com.plog.domain.report.entity;

/** 활동이 발생한 원천 도메인. Plog 내부(TASK/CHAT/POST/EVALUATION)와 외부 연동(GITHUB/FIGMA/NOTION/GOOGLE)을 구분한다. */
public enum SourceDomain {
    TASK, CHAT, POST, EVALUATION,
    GITHUB, FIGMA, NOTION, GOOGLE
}