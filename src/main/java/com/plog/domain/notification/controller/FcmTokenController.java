package com.plog.domain.notification.controller;

import com.plog.domain.notification.dto.FcmTokenDto;
import com.plog.domain.notification.service.FcmTokenService;
import com.plog.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/fcm-token")
@RequiredArgsConstructor
public class FcmTokenController {
    private final FcmTokenService fcmTokenService;

    @PutMapping
    public ApiResponse<FcmTokenDto.Response> put(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody FcmTokenDto.Request request
    ) {
        return ApiResponse.success(fcmTokenService.put(userId, request));
    }

    @DeleteMapping
    public ApiResponse<FcmTokenDto.DeletedResponse> delete(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody FcmTokenDto.Request request
    ) {
        return ApiResponse.success(fcmTokenService.delete(userId, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FcmTokenDto.Response>> registerFcmToken(
            @AuthenticationPrincipal(expression = "userId") Long userId,
            @Valid @RequestBody FcmTokenDto.Request request
    ) {
        FcmTokenDto.Response response = fcmTokenService.put(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}