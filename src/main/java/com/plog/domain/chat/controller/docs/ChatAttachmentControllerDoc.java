package com.plog.domain.chat.controller.docs;

import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

@Tag(name = "Chat", description = "채팅 API")
public interface ChatAttachmentControllerDoc {

    @Operation(
            summary = "채팅 첨부 원본 조회 (프록시)",
            description = """
                    채팅 첨부파일의 원본 바이트를 그대로 내려보냅니다. presigned URL 과 달리
                    서명·만료·쿼리스트링이 없어 이 URL 은 영구 고정입니다.
                    - `<img src>` 로 직접 걸어 쓰는 것을 전제로 합니다.
                    - 인증: `Authorization: Bearer` 헤더 또는 `plog_media` 쿠키.
                      `<img>` 는 헤더를 실을 수 없으므로 로그인/재발급 응답이 심어준 쿠키가
                      자동으로 전송됩니다. 프론트는 인증 API 호출에 `withCredentials: true` 를
                      켜야 합니다.
                    - 요청마다 해당 채팅방 프로젝트의 ACTIVE 멤버인지 검사합니다(CHAT002).
                    - 응답은 1년 `immutable` 로 캐시됩니다. 같은 자원의 바이트는 변하지 않습니다.
                    - 401/404 는 `<img>` 에서 '깨진 이미지'로만 보입니다. 프론트는 `onerror`
                      에서 `/api/auth/reissue` 로 쿠키를 재발급받고 `src` 를 한 번만 다시
                      설정하세요(무한 재시도 금지).
                    - `If-None-Match` 를 보내면 ETag 가 같을 때 304를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "첨부 원본 바이트",
                    content = @Content(mediaType = "application/octet-stream",
                            schema = @Schema(type = "string", format = "binary"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "304",
                    description = "If-None-Match 가 현재 ETag 와 일치 — 본문 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않음 (쿠키 만료 포함)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "채팅방 접근 권한 없음 (CHAT002)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 첨부파일 (CHAT010)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<Resource> download(
            @Parameter(description = "채팅 첨부 ID", example = "3") Long chatAttachmentId,
            Long userId,
            @Parameter(hidden = true) WebRequest webRequest,
            @Parameter(hidden = true) HttpServletResponse response
    );
}
