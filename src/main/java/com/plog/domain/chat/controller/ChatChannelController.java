package com.plog.domain.chat.controller;

import com.plog.domain.chat.controller.docs.ChatChannelControllerDoc;
import com.plog.domain.chat.dto.response.ChatChannelResponse;
import com.plog.domain.chat.service.ChatChannelListService;
import com.plog.domain.chat.service.ChatChannelSearchService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.ChatSuccessCode;
import com.plog.global.api.response.SliceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard/channels")
public class ChatChannelController implements ChatChannelControllerDoc {

    private final ChatChannelListService chatChannelListService;
    private final ChatChannelSearchService chatChannelSearchService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<SliceResponse<ChatChannelResponse>>> getChannels(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SliceResponse<ChatChannelResponse> response = chatChannelListService.getChannels(
                userId,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(ChatSuccessCode.CHANNEL_LIST_RETRIEVED, response));
    }

    @Override
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SliceResponse<ChatChannelResponse>>> searchChannels(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SliceResponse<ChatChannelResponse> response = chatChannelSearchService.search(
                userId,
                keyword,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(ChatSuccessCode.CHANNEL_SEARCH_RETRIEVED, response));
    }
}
