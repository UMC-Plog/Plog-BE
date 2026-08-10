package com.plog.domain.task.event;

import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDateTime;

/**
 * 업무카드 상태가 실제로 바뀌었을 때만 발행된다(같은 상태로의 PATCH는 발행하지 않음).
 * report 도메인의 0단계 수집({@code TaskActivityLogListener})이 구독한다.
 */
public record TaskStatusChangedEvent(
        Long taskId,
        Long projectMemberId,
        TaskStatus previousStatus,
        TaskStatus newStatus,
        LocalDateTime occurredAt
) {
}