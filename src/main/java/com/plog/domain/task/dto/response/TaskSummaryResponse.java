package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDate;

public record TaskSummaryResponse(
        Long taskId,
        String title,
        TaskCategory category,
        TaskStatus cardStatus,
        LocalDate endDate,
        boolean overdue, // 저장값 아님 — 응답 시점에 계산 (endDate 지났는데 DONE이 아니면 true)
        Long assigneeProjectMemberId,
        String assigneeNickname
) {
    public static TaskSummaryResponse from(Task task) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                isOverdue(task),
                task.getProjectMember().getId(),
                task.getProjectMember().getAnNickname()
        );
    }

    private static boolean isOverdue(Task task) {
        return task.getEndDate() != null
                && task.getCardStatus() != TaskStatus.DONE
                && task.getEndDate().isBefore(LocalDate.now());
    }
}