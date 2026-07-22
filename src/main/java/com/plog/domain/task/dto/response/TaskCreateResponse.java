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
        // FILE 첨부는 저장값이 S3 키라 그대로 내보낼 수 없다. 서비스가 발급한 URL을 받는다.
        public static AttachmentResponse of(TaskAttachment attachment, String resolvedUrl) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getAttachmentType(),
                    attachment.getFileName(),
                    resolvedUrl
            );
        }
    }

    public static TaskCreateResponse from(Task task, List<AttachmentResponse> attachments) {
        return new TaskCreateResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                task.getProjectMember().getId(),
                attachments
        );
    }
}