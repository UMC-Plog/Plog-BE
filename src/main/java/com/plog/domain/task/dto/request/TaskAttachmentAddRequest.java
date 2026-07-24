package com.plog.domain.task.dto.request;

import com.plog.domain.task.entity.AttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// TaskCreateRequest.TaskAttachmentRequest와 필드 구성이 동일 — 등록 API 전용 최상위 DTO로 분리
public record TaskAttachmentAddRequest(
        @NotNull(message = "첨부 유형은 필수입니다.")
        AttachmentType attachmentType,

        @NotBlank(message = "첨부 이름은 필수입니다.")
        String fileName,

        @PositiveOrZero(message = "파일 크기는 0 이상이어야 합니다.")
        Long fileSize,

        @Size(max = 512, message = "URL 길이는 512자를 초과할 수 없습니다.")
        String fileUrl,

        @Size(max = 512, message = "파일 키 길이는 512자를 초과할 수 없습니다.")
        String fileKey
) {
}