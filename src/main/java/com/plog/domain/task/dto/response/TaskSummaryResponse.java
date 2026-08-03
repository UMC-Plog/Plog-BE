package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.global.util.TimeUtil;
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
                isOverdue(task),
                AssigneeResponse.from(task),
                attachmentCount
        );
    }

    // 마감일 초과는 별도 상태값이 아니라 조회 시점에 계산한다.
    // DONE인 경우 completedAt이 마감일 이후인지로 판단한다.
    private static boolean isOverdue(Task task) {
        if (task.getEndDate() == null) {
            return false;
        }
        if (task.getCardStatus() == TaskStatus.DONE) {
            return isCompletedAfterDeadline(task);
        }
        return task.getEndDate().isBefore(LocalDate.now());
    }

    // completedAt은 UTC 저장값이라 마감일(KST 달력 기준)과 비교하려면 표시 타임존으로 환산해야 한다.
    private static boolean isCompletedAfterDeadline(Task task) {
        if (task.getCompletedAt() == null) {
            return false;
        }
        LocalDate completedDate = task.getCompletedAt()
                .atZone(TimeUtil.STORAGE_ZONE)
                .withZoneSameInstant(TimeUtil.DISPLAY_ZONE)
                .toLocalDate();
        return completedDate.isAfter(task.getEndDate());
    }
}
