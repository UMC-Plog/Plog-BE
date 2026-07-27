package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;

public record TaskAttachmentAddResponse(
        Long taskAttachmentId,
        AttachmentType attachmentType,
        Long fileId,
        String fileName,
        String fileUrl
) {
    public static TaskAttachmentAddResponse of(TaskAttachment attachment, String resolvedUrl) {
        return new TaskAttachmentAddResponse(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getUploadedFile() == null ? null : attachment.getUploadedFile().getId(),
                attachment.displayName(),
                resolvedUrl
        );
    }
}