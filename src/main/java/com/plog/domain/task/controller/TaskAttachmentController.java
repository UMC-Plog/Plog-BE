package com.plog.domain.task.controller;

import com.plog.domain.task.controller.docs.TaskAttachmentControllerDoc;
import com.plog.domain.task.service.TaskAttachmentDownloadService;
import com.plog.global.api.code.SuccessCode;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.AttachmentDownloadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 업무카드 첨부 다운로드 URL 발급. 첨부 관심사를 TaskController 에서 분리한다 —
 * TaskController 는 이미 엔드포인트 9개다.
 * <p>
 * 경로가 tasks/attachments 라 리터럴이 TaskController 의 /{taskId}/attachments 템플릿보다
 * 먼저 매칭된다. 현재 TaskController 에는 POST/DELETE 만 있어 GET 충돌은 없다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/tasks/attachments")
public class TaskAttachmentController implements TaskAttachmentControllerDoc {

    private final TaskAttachmentDownloadService taskAttachmentDownloadService;

    @Override
    @GetMapping("/{taskAttachmentId}/download-url")
    public ResponseEntity<ApiResponse<AttachmentDownloadResponse>> createDownloadUrl(
            @PathVariable Long projectId,
            @PathVariable Long taskAttachmentId,
            @AuthenticationPrincipal Long userId
    ) {
        AttachmentDownloadResponse response = taskAttachmentDownloadService
                .createDownloadUrl(projectId, taskAttachmentId, userId);
        return ResponseEntity.status(SuccessCode.OK.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.OK, response));
    }
}
