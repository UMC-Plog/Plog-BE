package com.plog.domain.report.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.plog.domain.task.entity.TaskStatus;

/**
 * {@code TASK_STATUS_CHANGE} 활동 로그의 {@code metadata} 컬럼 스키마.
 * <p>
 * 이미 생산자({@link TaskActivityLogService})와 소비자({@link ActivityClassificationRules},
 * 정확히는 {@code newStatus} 필드)가 붙어 있어 필드를 함부로 빼거나 이름을 바꾸면 과거 데이터와
 * 어긋난다. {@link #schemaVersion}을 처음부터 박아 둬서, 다음에 필드를 바꿔야 할 때 과거 행과
 * 새 행을 구분할 수 있게 한다.
 *
 * @param taskId         감사용 — sourceRefId에도 taskId가 들어가지만 메타데이터만 보고도 알 수 있게
 * @param previousStatus 이전 상태. 정상 경로(TaskStatusService)는 항상 채우지만, 안전망 재수집
 *                        경로({@code TaskActivityLogRecoveryScheduler})는 Task가 상태 이력을
 *                        저장하지 않아 이전 상태를 알 수 없으므로 null일 수 있다
 * @param newStatus      전이 후 상태. {@link ActivityClassificationRules#classifyContentless}가
 *                        이 필드만 읽어 분류한다(이하 두 필드는 감사용, 분류에 쓰지 않음)
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaskStatusChangeMetadata(
        int schemaVersion,
        Long taskId,
        TaskStatus previousStatus,
        TaskStatus newStatus
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static TaskStatusChangeMetadata of(Long taskId, TaskStatus previousStatus, TaskStatus newStatus) {
        return new TaskStatusChangeMetadata(CURRENT_SCHEMA_VERSION, taskId, previousStatus, newStatus);
    }
}