package com.plog.domain.integration.entity;

public enum ActivityType {
    COMMIT, PULL_REQUEST, ISSUE,   // GitHub
    PAGE_EDIT,                     // Notion
    FILE_EDIT,                     // Figma (버전 단위)
    COMMENT                        // Notion/Figma 댓글
}
