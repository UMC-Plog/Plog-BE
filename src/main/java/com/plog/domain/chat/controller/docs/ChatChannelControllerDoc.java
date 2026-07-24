package com.plog.domain.chat.controller.docs;

import com.plog.domain.chat.dto.response.ChatChannelResponse;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

@Tag(name = "Chat", description = "채팅 API")
public interface ChatChannelControllerDoc {

    @Operation(
            summary = "통합 채널 목록 조회",
            description = """
                    ACTIVE 멤버십의 프로젝트 채팅방을 최신 메시지 시각순으로 Slice 조회합니다.
                    각 프로젝트는 채팅방 1개를 가지며, 응답에는 제목(projectName), 마지막 메시지/시각,
                    읽지 않은 메시지 여부/개수, 채팅방 이미지 구성에 사용할 참여자 프로필 목록이 포함됩니다.
                    """
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
    ResponseEntity<ApiResponse<SliceResponse<ChatChannelResponse>>> getChannels(
            Long userId,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "채팅방 검색",
            description = """
                    ACTIVE 멤버십의 프로젝트 채팅방을 프로젝트명으로 검색합니다.
                    빈 keyword면 일반 목록과 같은 기준으로 조회되며, 응답 필드는 통합 채널 목록 조회와 동일합니다.
                    """
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
    ResponseEntity<ApiResponse<SliceResponse<ChatChannelResponse>>> searchChannels(
            Long userId,
            String keyword,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    );
}
