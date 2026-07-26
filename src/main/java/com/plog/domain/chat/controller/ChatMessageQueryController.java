package com.plog.domain.chat.controller;

import com.plog.domain.chat.dto.response.ChatMessageListResponse;
import com.plog.domain.chat.service.ChatMessageQueryService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ChatSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "채팅 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-rooms/{roomId}/messages")
public class ChatMessageQueryController {

    private final ChatMessageQueryService chatMessageQueryService;

    @Operation(
            summary = "채팅 메시지 목록 조회",
            description = """
                    채팅방의 메시지 목록을 커서 기반으로 조회합니다.
                    - 로그인 사용자가 해당 채팅방이 속한 프로젝트의 활성(ACTIVE) 멤버가 아니면 접근할 수 없습니다(CHAT002).
                    - before를 생략하면 가장 최신 메시지부터 최대 size개를 가져옵니다.
                    - before를 넘기면 해당 messageSequence보다 이전(과거) 메시지를 가져옵니다.
                      (직전 응답의 nextCursor를 그대로 넘기면 됨)
                    - 응답의 messages는 과거 → 최신 순으로 정렬되어 내려갑니다(화면에 바로 렌더링 가능).
                    - size 기본값 30, 최대 100.
                    - 인증 필요(Access Token).
                    """
    )
    @GetMapping
    public ApiResponse<ChatMessageListResponse> getMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer size
    ) {
        ChatMessageListResponse response = chatMessageQueryService.getMessages(roomId, userId, before, size);
        return ApiResponse.success(ChatSuccessCode.MESSAGE_LIST_RETRIEVED, response);
    }
}