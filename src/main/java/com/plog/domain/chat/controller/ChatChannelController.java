package com.plog.domain.chat.controller;

import com.plog.domain.chat.dto.response.ChatChannelListResponse;
import com.plog.domain.chat.dto.response.ChatChannelSearchResponse;
import com.plog.domain.chat.service.ChatChannelListService;
import com.plog.domain.chat.service.ChatChannelSearchService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ChatSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "채팅 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard/channels")
public class ChatChannelController {

    private final ChatChannelListService chatChannelListService;
    private final ChatChannelSearchService chatChannelSearchService;

    @Operation(
            summary = "통합 채널 목록 조회",
            description = "ACTIVE 멤버십의 프로젝트 채팅방을 최신 메시지 시각순으로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "통합 채널 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 조건",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<ApiResponse<ChatChannelListResponse>> getChannels(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ChatChannelListResponse response = chatChannelListService.getChannels(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(ChatSuccessCode.CHANNEL_LIST_RETRIEVED, response));
    }

    @Operation(
            summary = "채팅방 검색",
            description = "ACTIVE 멤버십의 프로젝트 채팅방을 프로젝트명으로 검색합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "채팅방 검색 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 검색어 또는 페이지 조건",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ChatChannelSearchResponse>> searchChannels(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ChatChannelSearchResponse response = chatChannelSearchService.search(
                userId,
                keyword,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(ChatSuccessCode.CHANNEL_SEARCH_RETRIEVED, response));
    }
}
