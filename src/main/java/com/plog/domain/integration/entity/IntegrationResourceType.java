package com.plog.domain.integration.entity;

/** 프로젝트에서 수집 대상으로 선택한 provider 리소스의 종류다. */
public enum IntegrationResourceType {
    GITHUB_REPOSITORY,
    NOTION_PAGE,
    NOTION_DATA_SOURCE,
    GOOGLE_DOCUMENT,
    GOOGLE_PRESENTATION,
    FIGMA_FILE
}
