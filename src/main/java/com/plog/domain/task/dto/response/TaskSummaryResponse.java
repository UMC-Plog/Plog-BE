package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskOverdueCalculator;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.ProfilePreset;
import java.time.LocalDate;

public record TaskSummaryResponse(
        Long taskId,
        String title,
        TaskCategory category,
        TaskStatus cardStatus,
        LocalDate endDate,
        boolean isOverdue, // 저장값 아님 — 응답 시점에 계산 (endDate 지났는데 DONE이 아니면 true)
        AssigneeResponse assignee,
        int attachmentCount
) {

    public record AssigneeResponse(
            Long projectMemberId,
            String nickname,
            ProfilePreset profilePreset // ChatChannelParticipantResponse/ProjectListResponse와 동일한 필드명 컨벤션
    ) {
        public static AssigneeResponse from(Task task) {
            return new AssigneeResponse(
                    task.getProjectMember().getId(),
                    task.getProjectMember().getDisplayNickname(),
                    task.getProjectMember().getUser().getProfilePreset()
            );
        }
    }

    public static TaskSummaryResponse from(Task task, int attachmentCount) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                TaskOverdueCalculator.isOverdue(task),
                AssigneeResponse.from(task),
                attachmentCount
        );
    }
}
