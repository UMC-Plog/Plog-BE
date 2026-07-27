package com.plog.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "채팅 메시지 목록 조회 응답")
public record ChatMessageListResponse(
        @Schema(description = "메시지 목록 (과거 → 최신 순)")
        List<ChatMessageResponse> messages,
        @Schema(description = "더 과거 메시지가 있는지 여부", example = "true")
        boolean hasNext,
        @Schema(description = "다음 페이지 요청 시 before로 넘길 커서. hasNext가 false면 null", example = "42")
        Long nextCursor
) {
}