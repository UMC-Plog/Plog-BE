package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
            Long fileId,
            String fileName,
            @Schema(description = "LINK 첨부의 외부 링크. FILE 이면 null")
            String linkUrl,
            @Schema(description = "FILE 첨부의 다운로드 URL 발급 API 주소. 클릭 시 이 주소를 "
                    + "호출해 presigned 를 받는다. LINK 면 null. "
                    + "이 주소를 <a href> 에 걸면 JSON 이 보인다")
            String downloadUrlApi
    ) {
        // FILE 첨부는 저장값이 S3 키라 그대로 내보낼 수 없다. presigned 를 미리 담으면
        // 조회 시점에 만료 시계가 시작되므로, 발급 API 주소만 주고 클릭 시점에 받아가게 한다.
        public static AttachmentResponse of(TaskAttachment attachment, String downloadUrlApi) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getAttachmentType(),
                    attachment.getUploadedFile() == null
                            ? null : attachment.getUploadedFile().getId(),
                    attachment.displayName(),
                    attachment.getLinkUrl(),
                    downloadUrlApi
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