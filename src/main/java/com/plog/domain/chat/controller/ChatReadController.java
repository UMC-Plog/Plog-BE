package com.plog.domain.chat.controller;

import com.plog.domain.chat.dto.request.ChatReadUpdateRequest;
import com.plog.domain.chat.dto.response.ChatReadResponse;
import com.plog.domain.chat.service.ChatReadService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ChatSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "채팅 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-rooms/{roomId}")
public class ChatReadController {

    private final ChatReadService chatReadService;

    @Operation(
            summary = "채팅방 읽음 처리",
            description = """
                    채팅방에서 마지막으로 읽은 메시지 위치를 갱신합니다.
                    - 로그인 사용자가 해당 채팅방이 속한 프로젝트의 활성(ACTIVE) 멤버가 아니면 접근할 수 없습니다(CHAT002).
                    - lastReadMessageId는 이 채팅방에 실제로 존재하는 메시지의 chatId여야 합니다.
                      존재하지 않거나 다른 채팅방의 메시지면 CHAT007을 반환합니다.
                    - 이미 읽은 위치보다 과거이거나 같은 메시지로 다시 요청해도 에러 없이
                      현재 읽음 상태를 그대로 반환합니다(멱등).
                    - 동시에 여러 요청이 들어와도 읽음 위치는 뒤로 가지 않습니다.
                    - 인증 필요(Access Token).
                    """
    )
    @PatchMapping("/read")
    public ApiResponse<ChatReadResponse> markAsRead(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChatReadUpdateRequest request
    ) {
        ChatReadResponse response = chatReadService.markAsRead(roomId, userId, request.lastReadMessageId());
        return ApiResponse.success(ChatSuccessCode.CHAT_ROOM_READ_UPDATED, response);
    }
}