package com.plog.domain.report.entity;

/**
 * 도메인별 원본 활동 세부 유형. 외부 연동 값은 {@code IntegrationActivityType}과 이름을 맞췄지만
 * report 도메인이 integration 도메인에 의존하지 않도록 별도로 정의한다(상완이 변환 시 매핑).
 * 각 값은 자신이 속한 {@link SourceDomain}을 갖는다 — {@code ReportActivityLog.create}가 이걸로
 * sourceDomain/rawActivityType 조합이 잘못 저장되는 것(예: TASK + GITHUB_COMMIT)을 막는다.
 */
public enum RawActivityType {

    // TASK
    TASK_STATUS_CHANGE(SourceDomain.TASK),
    TASK_ATTACHMENT_ADD(SourceDomain.TASK),

    // CHAT
    CHAT_MESSAGE(SourceDomain.CHAT),

    // POST
    POST_CREATE(SourceDomain.POST),
    COMMENT_CREATE(SourceDomain.POST),

    // EVALUATION
    PEER_EVALUATION_SUBMIT(SourceDomain.EVALUATION),
    SELF_FEEDBACK_SUBMIT(SourceDomain.EVALUATION),

    // GITHUB
    GITHUB_COMMIT(SourceDomain.GITHUB),
    GITHUB_PULL_REQUEST(SourceDomain.GITHUB),
    GITHUB_PULL_REQUEST_REVIEW(SourceDomain.GITHUB),
    GITHUB_ISSUE(SourceDomain.GITHUB),
    GITHUB_ISSUE_COMMENT(SourceDomain.GITHUB),
    GITHUB_ISSUE_EVENT(SourceDomain.GITHUB),

    // FIGMA
    FIGMA_FILE_VERSION(SourceDomain.FIGMA),
    FIGMA_COMMENT(SourceDomain.FIGMA),
    FIGMA_FILE_METADATA(SourceDomain.FIGMA),
    FIGMA_COMMENT_REACTION(SourceDomain.FIGMA),

    // GOOGLE
    GOOGLE_DRIVE_ACTIVITY(SourceDomain.GOOGLE),
    GOOGLE_DRIVE_REVISION(SourceDomain.GOOGLE),
    GOOGLE_DOCUMENT_SUGGESTION(SourceDomain.GOOGLE),
    GOOGLE_DRIVE_COMMENT(SourceDomain.GOOGLE),
    GOOGLE_DRIVE_FILE_SNAPSHOT(SourceDomain.GOOGLE),
    GOOGLE_PRESENTATION_SNAPSHOT(SourceDomain.GOOGLE),

    // NOTION (점수 계산 제외, 근거 텍스트/신뢰도 등급 용도로만 사용)
    NOTION_DATA_SOURCE_SNAPSHOT(SourceDomain.NOTION),
    NOTION_PAGE_SNAPSHOT(SourceDomain.NOTION),
    NOTION_BLOCK_SNAPSHOT(SourceDomain.NOTION),
    NOTION_COMMENT(SourceDomain.NOTION);

    private final SourceDomain owningDomain;

    RawActivityType(SourceDomain owningDomain) {
        this.owningDomain = owningDomain;
    }

    public SourceDomain owningDomain() {
        return owningDomain;
    }
}