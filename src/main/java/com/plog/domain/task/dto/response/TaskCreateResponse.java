package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDate;
import java.util.List;

public record TaskCreateResponse(
        Long taskId,
        String title,
        TaskCategory category,
        TaskStatus cardStatus,
        LocalDate endDate,
        Long projectMemberId,
        List<AttachmentResponse> attachments
) {

    public record AttachmentResponse(
            Long taskAttachmentId,
            AttachmentType attachmentType,
            String fileName,
            String fileUrl
    ) {
        public static AttachmentResponse from(TaskAttachment attachment) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getAttachmentType(),
                    attachment.getFileName(),
                    attachment.getFileUrl()
            );
        }
    }

    public static TaskCreateResponse from(Task task, List<TaskAttachment> attachments) {
        return new TaskCreateResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                task.getProjectMember().getId(),
                attachments.stream().map(AttachmentResponse::from).toList()
        );
    }
}