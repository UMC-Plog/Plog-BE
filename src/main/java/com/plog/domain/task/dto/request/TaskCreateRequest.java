package com.plog.domain.task.dto.request;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record TaskCreateRequest(
        @NotBlank @Size(min = 2, message = "업무명은 2자 이상이어야 합니다.")
        String title,

        @NotNull
        Long projectMemberId,

        @NotNull
        TaskCategory category,

        @NotNull
        TaskStatus cardStatus,

        @NotNull
        LocalDate endDate,

        List<@NotNull @Valid TaskAttachmentRequest> attachments // 선택값, 미전달 시 null 또는 빈 리스트
) {
    public record TaskAttachmentRequest(
            @NotNull(message = "첨부 유형은 필수입니다.")
            AttachmentType attachmentType,

            @NotBlank(message = "첨부 이름은 필수입니다.")
            String fileName,

            @PositiveOrZero(message = "파일 크기는 0 이상이어야 합니다.")
            Long fileSize,

            @NotBlank(message = "첨부 URL은 필수입니다.")
            @Size(max = 512, message = "URL 길이는 512자를 초과할 수 없습니다.")
            String fileUrl
    ) {
    }
}