package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDate;
import java.util.List;

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
            String profileImageUrl // ChatChannelParticipantResponse/ProjectListResponse와 동일한 필드명 컨벤션
    ) {
        public static AssigneeResponse from(Task task) {
            return new AssigneeResponse(
                    task.getProjectMember().getId(),
                    task.getProjectMember().getAnNickname(),
                    task.getProjectMember().getUser().getProfileImageUrl()
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
                isOverdue(task),
                AssigneeResponse.from(task),
                attachmentCount
        );
    }

    // 마감일 초과는 별도 상태값이 아니라 조회 시점에 계산한다.
    // (기능명세: 상태는 예정/진행중/완료 3가지만 존재, "지연" 상태값은 두지 않음)
    private static boolean isOverdue(Task task) {
        return task.getEndDate() != null
                && task.getCardStatus() != TaskStatus.DONE
                && task.getEndDate().isBefore(LocalDate.now());
    }
}