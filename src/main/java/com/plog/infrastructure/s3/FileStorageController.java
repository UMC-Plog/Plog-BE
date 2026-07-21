package com.plog.infrastructure.s3;

import com.plog.global.api.code.SuccessCode;
import com.plog.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileStorageController {
    private final FileStorageService fileStorageService;

    @PostMapping("/presigned-upload-url")
    public ResponseEntity<ApiResponse<FileStorageDto.PresignedUploadResponse>> createPresignedUploadUrl(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FileStorageDto.PresignedUploadRequest request
    ) {
        var response = fileStorageService.createUploadUrl(userId, request);
        return ResponseEntity.status(SuccessCode.CREATED.getHttpStatus())
                .body(ApiResponse.success(SuccessCode.CREATED, response));
    }
}
