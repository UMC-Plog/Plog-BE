package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import io.swagger.v3.oas.annotations.media.Schema;

public record TaskAttachmentAddResponse(
        Long taskAttachmentId,
        AttachmentType attachmentType,
        Long fileId,
        String fileName,
        @Schema(description = "LINK 첨부의 외부 링크. FILE 이면 null")
        String linkUrl,
        @Schema(description = "FILE 첨부의 다운로드 URL 발급 API 주소. 클릭 시 이 주소를 "
                + "호출해 presigned 를 받는다. LINK 면 null. "
                + "이 주소를 <a href> 에 걸면 JSON 이 보인다")
        String downloadUrlApi
) {
    public static TaskAttachmentAddResponse of(TaskAttachment attachment, String downloadUrlApi) {
        return new TaskAttachmentAddResponse(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getUploadedFile() == null ? null : attachment.getUploadedFile().getId(),
                attachment.displayName(),
                attachment.getLinkUrl(),
                downloadUrlApi
        );
    }
}
