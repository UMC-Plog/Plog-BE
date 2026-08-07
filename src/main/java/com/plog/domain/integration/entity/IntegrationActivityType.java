package com.plog.domain.integration.entity;

/** provider 원문을 보존하는 최소 활동 분류다. 기여도 계산 규칙은 이 분류에 두지 않는다. */
public enum IntegrationActivityType {
    GITHUB_COMMIT(true),
    GITHUB_PULL_REQUEST(true),
    GITHUB_PULL_REQUEST_REVIEW(true),
    GITHUB_ISSUE(true),
    GITHUB_ISSUE_COMMENT(true),
    GITHUB_ISSUE_EVENT(true),
    NOTION_DATA_SOURCE_SNAPSHOT(true),
    NOTION_PAGE_SNAPSHOT(true),
    NOTION_BLOCK_SNAPSHOT(true),
    NOTION_COMMENT(true),
    NOTION_WEBHOOK_EVENT(true),
    GOOGLE_DRIVE_FILE_SNAPSHOT(true),
    GOOGLE_DRIVE_ACTIVITY(true),
    GOOGLE_DRIVE_COMMENT(true),
    GOOGLE_DRIVE_REVISION(true),
    GOOGLE_DOCUMENT_SUGGESTION(false),
    GOOGLE_PRESENTATION_SNAPSHOT(false),
    FIGMA_FILE_VERSION(true),
    FIGMA_FILE_METADATA(false),
    FIGMA_COMMENT(true),
    FIGMA_COMMENT_REACTION(true);

    private final boolean actorDisplayRequired;

    IntegrationActivityType(boolean actorDisplayRequired) {
        this.actorDisplayRequired = actorDisplayRequired;
    }

    /** actor가 있는 활동인지, actor 없이 provider 원문 자체를 보존하는 스냅샷인지 구분한다. */
    public boolean requiresActorDisplay() {
        return actorDisplayRequired;
    }
}
