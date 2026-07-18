package com.plog.domain.task.dto.request;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record TaskCreateRequest(
        @NotBlank @Size(min = 2, message = "업무명은 2자 이상이어야 합니다.") String title,
        @NotNull Long projectMemberId,
        @NotNull TaskCategory category,
        @NotNull TaskStatus cardStatus,
        LocalDate endDate, // 선택값
        List<@Valid TaskAttachmentRequest> attachments // 선택값, 미전달 시 null 또는 빈 리스트
) {

    public record TaskAttachmentRequest(
            @NotNull AttachmentType attachmentType,
            String fileName,
            Long fileSize,
            @NotBlank String fileUrl
    ) {
    }
}