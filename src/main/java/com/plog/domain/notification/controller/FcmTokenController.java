package com.plog.domain.notification.controller;

import com.plog.domain.notification.dto.FcmTokenDto;
import com.plog.domain.notification.service.FcmTokenService;
import com.plog.global.api.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/fcm-token")
@RequiredArgsConstructor
public class FcmTokenController {
    private final FcmTokenService fcmTokenService;

    @PutMapping
    public ApiResponse<FcmTokenDto.Response> put(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FcmTokenDto.Request request
    ) {
        return ApiResponse.success(fcmTokenService.put(userId, request));
    }

    @DeleteMapping
    public ApiResponse<FcmTokenDto.DeletedResponse> delete(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FcmTokenDto.Request request
    ) {
        return ApiResponse.success(fcmTokenService.delete(userId, request));
    }
}
