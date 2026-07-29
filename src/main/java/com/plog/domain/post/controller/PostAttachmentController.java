package com.plog.domain.post.controller;

import com.plog.domain.post.controller.docs.PostAttachmentControllerDoc;
import com.plog.domain.post.service.PostAttachmentDownloadService;
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
 * 게시글 첨부 다운로드 URL 발급. 첨부 관심사를 PostController 에서 분리한다 —
 * project 도메인이 관심사별로 컨트롤러를 나눈 규약과 같고, PostController 는 이미
 * 엔드포인트 11개로 가장 크다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/posts/attachments")
public class PostAttachmentController implements PostAttachmentControllerDoc {

    private final PostAttachmentDownloadService postAttachmentDownloadService;

    @Override
    @GetMapping("/{postAttachmentId}/download-url")
    public ResponseEntity<ApiResponse<AttachmentDownloadResponse>> createDownloadUrl(
            @PathVariable Long projectId,
            @PathVariable Long postAttachmentId,
            @AuthenticationPrincipal Long userId
    ) {
        AttachmentDownloadResponse response = postAttachmentDownloadService
                .createDownloadUrl(projectId, postAttachmentId, userId);
        return ResponseEntity.status(SuccessCode.OK.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.OK, response));
    }
}
