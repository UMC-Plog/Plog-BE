package com.plog.domain.chat.controller;

import com.plog.domain.chat.dto.response.ChatMessageResponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * roomId 하위가 아니라 최상위 경로로 둔다 — 알림 센터 등에서 roomId 없이 chatId만 아는
 * 상황(딥링크)에서 조회할 수 있어야 하기 때문이다. 권한 검증은 메시지에서 역으로 room을
 * 찾아 수행한다.
 */
@Tag(name = "Chat", description = "채팅 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-messages")
public class ChatMessageDetailController {

    private final ChatMessageQueryService chatMessageQueryService;

    @Operation(
            summary = "채팅 메시지 상세 조회",
            description = """
                    메시지 ID(chatId)로 채팅 메시지 단건을 조회합니다.
                    - roomId를 몰라도 조회 가능합니다(메시지에서 역으로 room을 찾아 권한을 검증합니다).
                    - 로그인 사용자가 해당 메시지가 속한 프로젝트의 ACTIVE 멤버가 아니면 접근할 수 없습니다(CHAT002).
                    - 존재하지 않는 메시지는 CHAT007로 응답합니다.
                    - 첨부파일 URL은 만료 없는 프록시 URL입니다(presigned 아님).
                    - 인증 필요(Access Token).
                    """
    )
    @GetMapping("/{chatId}")
    public ApiResponse<ChatMessageResponse> getMessageDetail(
            @PathVariable Long chatId,
            @AuthenticationPrincipal Long userId
    ) {
        ChatMessageResponse response = chatMessageQueryService.getMessageDetail(chatId, userId);
        return ApiResponse.success(ChatSuccessCode.MESSAGE_DETAIL_RETRIEVED, response);
    }
}