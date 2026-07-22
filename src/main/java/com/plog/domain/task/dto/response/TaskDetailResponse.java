package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record TaskDetailResponse(
        Long taskId,
        String title,
        AssigneeResponse assignee,
        TaskCategory category,
        TaskStatus cardStatus,
        LocalDate endDate,
        LocalDateTime completedAt, // 상태가 DONE일 때만 값 존재, 그 외 null
        int dDay,        // endDate - 오늘. 마감일이 지났으면 음수. 배너 문구는 프론트에서 조립
        boolean isOverdue,
        boolean isImminent, // 마감 D-3 ~ D-0, 완료되지 않은 경우에만 true
        List<AttachmentResponse> attachments
) {

    // 마감 임박 기준: 마감일 3일 전부터 (기획 확정값, 화면 배너 표시 기준과 동일)
    private static final int IMMINENT_THRESHOLD_DAYS = 3;

    public record AssigneeResponse(
            Long projectMemberId,
            String nickname,
            String profileImageUrl
    ) {
        public static AssigneeResponse from(Task task) {
            return new AssigneeResponse(
                    task.getProjectMember().getId(),
                    task.getProjectMember().getAnNickname(),
                    task.getProjectMember().getUser().getProfileImageUrl()
            );
        }
    }

    public record AttachmentResponse(
            Long taskAttachmentId,
            AttachmentType attachmentType,
            String fileName,
            Long fileSize, // 바이트 단위. "5.2MB" 같은 표시 변환은 프론트 담당
            String fileUrl
    ) {
        public static AttachmentResponse of(TaskAttachment attachment, String resolvedUrl) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getAttachmentType(),
                    attachment.getFileName(),
                    attachment.getFileSize(),
                    resolvedUrl
            );
        }
    }

    public static TaskDetailResponse from(Task task, List<AttachmentResponse> attachments) {
        int dDay = calculateDDay(task);
        boolean overdue = isOverdue(task);
        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                AssigneeResponse.from(task),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                task.getCompletedAt(),
                dDay,
                overdue,
                isImminent(task, dDay, overdue),
                attachments
        );
    }

    private static int calculateDDay(Task task) {
        if (task.getEndDate() == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), task.getEndDate());
    }

    private static boolean isOverdue(Task task) {
        return task.getEndDate() != null
                && task.getCardStatus() != TaskStatus.DONE
                && task.getEndDate().isBefore(LocalDate.now());
    }

    // 마감이 이미 지난 경우(overdue)는 임박이 아니라 초과이므로 배타적으로 처리
    private static boolean isImminent(Task task, int dDay, boolean overdue) {
        return task.getEndDate() != null
                && task.getCardStatus() != TaskStatus.DONE
                && !overdue
                && dDay <= IMMINENT_THRESHOLD_DAYS;
    }
}