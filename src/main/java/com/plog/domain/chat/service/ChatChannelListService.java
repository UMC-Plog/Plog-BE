package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.chat.dto.response.ChatChannelResponse;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatChannelListService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatChannelParticipantService participantService;

    @Transactional(readOnly = true)
    public SliceResponse<ChatChannelResponse> getChannels(Long userId, int page, int size) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        Slice<ChatChannelSummary> channelSlice = chatRoomRepository.findChannelPage(
                userId,
                MemberStatus.ACTIVE,
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

    private ChatChannelResponse toChannel(
            ChatChannelSummary summary,
            List<ChatChannelParticipantResponse> participants
    ) {
        long unreadMessageCount = summary.getUnreadMessageCount();
        return new ChatChannelResponse(
                summary.getProjectId(),
                summary.getProjectName(),
                summary.getRoomId(),
                summary.getLatestMessage(),
                summary.getLatestMessageAt(),
                unreadMessageCount > 0,
                unreadMessageCount,
                participants
        );
    }
}
