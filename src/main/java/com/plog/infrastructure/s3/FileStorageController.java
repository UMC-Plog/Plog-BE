package com.plog.infrastructure.s3;

import com.plog.global.api.code.SuccessCode;
import com.plog.global.api.response.ApiResponse;
import com.plog.infrastructure.s3.docs.FileStorageControllerDoc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileStorageController implements FileStorageControllerDoc {
    private final UploadedFileService uploadedFileService;

    @Override
    @PostMapping("/presigned-upload-url")
    public ResponseEntity<ApiResponse<FileStorageDto.PresignedUploadResponse>> createPresignedUploadUrl(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FileStorageDto.PresignedUploadRequest request
    ) {
        var response = uploadedFileService.issue(userId, request);
        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.CREATED, response));
    }
}
