package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatChannelSearchResponse;
import com.plog.domain.chat.dto.response.ChatChannelSearchResponse.SearchChannel;
import com.plog.domain.chat.dto.response.ChatChannelSearchResponse.SearchPageInfo;
import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatChannelSearchService {

    private static final int MAX_KEYWORD_LENGTH = 20;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatChannelParticipantService participantService;

    @Transactional(readOnly = true)
    public ChatChannelSearchResponse search(Long userId, String keyword, int page, int size) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        String projectNamePattern = toSearchPattern(keyword);
        Page<ChatChannelSummary> channelPage = chatRoomRepository.findChannelPageByProjectName(
                userId,
                MemberStatus.ACTIVE,
                projectNamePattern,
                PageRequest.of(page, size)
        );
        List<Long> projectIds = channelPage.getContent().stream()
                .map(ChatChannelSummary::getProjectId)
                .toList();
        Map<Long, List<ChatChannelParticipantResponse>> participantsByProject =
                participantService.getParticipantsByProjectIds(projectIds);
        List<SearchChannel> content = channelPage.getContent().stream()
                .map(summary -> toChannel(
                        summary,
                        participantsByProject.getOrDefault(summary.getProjectId(), List.of())
                ))
                .toList();
        return new ChatChannelSearchResponse(
                content,
                new SearchPageInfo(
                        channelPage.getNumber(),
                        channelPage.getSize(),
                        channelPage.getTotalElements(),
                        channelPage.getTotalPages(),
                        channelPage.hasNext()
                )
        );
    }

    private String toSearchPattern(String keyword) {
        if (keyword == null) {
            throw new ApiException(ChatErrorCode.INVALID_SEARCH_KEYWORD);
        }
        String normalized = keyword.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new ApiException(ChatErrorCode.INVALID_SEARCH_KEYWORD);
        }
        String escaped = normalized.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private SearchChannel toChannel(
            ChatChannelSummary summary,
            List<ChatChannelParticipantResponse> participants
    ) {
        return new SearchChannel(
                summary.getProjectId(),
                summary.getProjectName(),
                summary.getRoomId(),
                summary.getLatestMessage(),
                summary.getLatestMessageAt(),
                summary.getUnreadMessageCount() > 0,
                summary.getUnreadMessageCount(),
                participants
        );
    }
}
