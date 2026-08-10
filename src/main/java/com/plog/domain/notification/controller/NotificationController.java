package com.plog.domain.notification.controller;

import com.plog.domain.notification.dto.response.NotificationResponse;
import com.plog.domain.notification.service.NotificationQueryService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.NotificationSuccessCode;
import com.plog.global.api.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 센터 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    // 다른 목록 API(채팅 메시지 목록, MAX_SIZE=100)와 동일한 상한 정책을 따른다.
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationQueryService notificationQueryService;

    @Operation(
            summary = "알림 센터 목록 조회",
            description = """
                    로그인 사용자의 알림을 최신순으로 조회합니다.
                    - 로그인 사용자 본인의 알림만 조회됩니다(다른 사용자 알림 접근 불가).
                    - page는 0 이상, size는 1 이상 100 이하여야 합니다(위반 시 400).
                    - page 기본값 0, size 기본값 20.
                    - 인증 필요(Access Token).
                    """
    )
    @GetMapping
    public ApiResponse<SliceResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size
    ) {
        SliceResponse<NotificationResponse> response = notificationQueryService.getNotifications(userId, page, size);
        return ApiResponse.success(NotificationSuccessCode.NOTIFICATION_LIST_RETRIEVED, response);
    }
}
