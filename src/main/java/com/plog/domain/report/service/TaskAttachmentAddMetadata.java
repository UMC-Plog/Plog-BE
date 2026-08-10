package com.plog.domain.report.service;

/** {@code TASK_ATTACHMENT_ADD} 활동 로그의 {@code metadata} 컬럼 스키마. */
public record TaskAttachmentAddMetadata(
        int schemaVersion,
        Long taskId,
        Long attachmentId
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static TaskAttachmentAddMetadata of(Long taskId, Long attachmentId) {
        return new TaskAttachmentAddMetadata(CURRENT_SCHEMA_VERSION, taskId, attachmentId);
    }
}