package com.plog.domain.chat.controller;

import com.plog.domain.chat.dto.response.MentionableMemberResponse;
import com.plog.domain.chat.service.ChatMentionableMemberService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ChatSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
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
@RequestMapping("/api/chat-rooms/{roomId}/mentionable-members")
public class ChatMentionableMemberController {

    private final ChatMentionableMemberService chatMentionableMemberService;

    @Operation(
            summary = "멘션 가능한 프로젝트 멤버 목록 조회",
            description = """
                    채팅 입력창에서 @ 입력 시 멘션 가능한 프로젝트 멤버 목록을 조회합니다.
                    - 로그인 사용자가 해당 채팅방이 속한 프로젝트의 ACTIVE 멤버가 아니면 접근할 수 없습니다(CHAT002).
                    - 비활성(EXIT) 멤버와 로그인 사용자 본인은 목록에서 제외됩니다.
                    - keyword를 넘기면 닉네임에 포함된 멤버만 필터링합니다(대소문자 무시).
                    - 닉네임 오름차순으로 정렬되어 내려갑니다. 페이지네이션 없음.
                    - 인증 필요(Access Token).
                    """
    )
    @GetMapping
    public ApiResponse<List<MentionableMemberResponse>> getMentionableMembers(
            @PathVariable Long roomId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String keyword
    ) {
        List<MentionableMemberResponse> response = chatMentionableMemberService
                .getMentionableMembers(roomId, userId, keyword);
        return ApiResponse.success(ChatSuccessCode.MENTIONABLE_MEMBER_LIST_RETRIEVED, response);
    }
}