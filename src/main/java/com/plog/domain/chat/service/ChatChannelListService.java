package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatChannelListResponse;
import com.plog.domain.chat.dto.response.ChatChannelListResponse.Channel;
import com.plog.domain.chat.dto.response.ChatChannelListResponse.PageInfo;
import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatChannelListService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatChannelParticipantService participantService;

    @Transactional(readOnly = true)
    public ChatChannelListResponse getChannels(Long userId, int page, int size) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        Page<ChatChannelSummary> channelPage = chatRoomRepository.findChannelPage(
                userId,
                MemberStatus.ACTIVE,
                PageRequest.of(page, size)
        );
        List<Long> projectIds = channelPage.getContent().stream()
                .map(ChatChannelSummary::getProjectId)
                .toList();
        Map<Long, List<ChatChannelParticipantResponse>> participantsByProject =
                participantService.getParticipantsByProjectIds(projectIds);
        List<Channel> content = channelPage.getContent().stream()
                .map(summary -> toChannel(
                        summary,
                        participantsByProject.getOrDefault(summary.getProjectId(), List.of())
                ))
                .toList();
        return new ChatChannelListResponse(
                content,
                new PageInfo(
                        channelPage.getNumber(),
                        channelPage.getSize(),
                        channelPage.getTotalElements(),
                        channelPage.getTotalPages(),
                        channelPage.hasNext()
                )
        );
    }

    private Channel toChannel(
            ChatChannelSummary summary,
            List<ChatChannelParticipantResponse> participants
    ) {
        long unreadMessageCount = summary.getUnreadMessageCount();
        return new Channel(
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
