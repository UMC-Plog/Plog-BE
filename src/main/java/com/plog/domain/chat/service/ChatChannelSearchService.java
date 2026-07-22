package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.chat.dto.response.ChatChannelResponse;
import com.plog.global.util.TimeUtil;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatChannelSearchService {

    private static final int MAX_KEYWORD_LENGTH = 20;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatChannelParticipantService participantService;

    @Transactional(readOnly = true)
    public SliceResponse<ChatChannelResponse> search(
            Long userId,
            String keyword,
            int page,
            int size
    ) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        String projectNamePattern = toSearchPattern(keyword);
        Slice<ChatChannelSummary> channelSlice = chatRoomRepository.findChannelPageByProjectName(
                userId,
                MemberStatus.ACTIVE,
                projectNamePattern,
                PageRequest.of(page, size)
        );
        List<Long> projectIds = channelSlice.getContent().stream()
                .map(ChatChannelSummary::getProjectId)
                .toList();
        Map<Long, List<ChatChannelParticipantResponse>> participantsByProject =
                participantService.getParticipantsByProjectIds(projectIds);
        List<ChatChannelResponse> content = channelSlice.getContent().stream()
                .map(summary -> toChannel(
                        summary,
                        participantsByProject.getOrDefault(summary.getProjectId(), List.of())
                ))
                .toList();
        return SliceResponse.of(channelSlice, content);
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

    private ChatChannelResponse toChannel(
            ChatChannelSummary summary,
            List<ChatChannelParticipantResponse> participants
    ) {
        return new ChatChannelResponse(
                summary.getProjectId(),
                summary.getProjectName(),
                summary.getRoomId(),
                summary.getLatestMessage(),
                TimeUtil.toInstant(summary.getLatestMessageAt()),
                summary.getUnreadMessageCount() > 0,
                summary.getUnreadMessageCount(),
                participants
        );
    }
}
