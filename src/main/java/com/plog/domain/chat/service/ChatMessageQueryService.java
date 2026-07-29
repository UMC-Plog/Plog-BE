package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatMessageListResponse;
import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageQueryService {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ChatAttachmentResponseMapper chatAttachmentResponseMapper;

    @Transactional(readOnly = true)
    public ChatMessageListResponse getMessages(Long roomId, Long userId, Long before, Integer size) {
        chatRoomRepository.findAccessibleRoom(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

        int pageSize = clampSize(size);

        // hasNext 판별을 위해 요청 size보다 1건 더 조회
        List<ChatMessage> fetched = chatMessageRepository.findPageBeforeCursor(
                roomId, before, PageRequest.of(0, pageSize + 1));

        boolean hasNext = fetched.size() > pageSize;
        List<ChatMessage> page = hasNext ? fetched.subList(0, pageSize) : fetched;

        Map<Long, List<ChatAttachment>> attachmentsByMessageId = chatAttachmentRepository
                .findAllByChatMessageIdInOrderByIdAsc(page.stream().map(ChatMessage::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getChatMessage().getId()));

        // DB에서는 최신순(desc)으로 뽑았으니, 화면 표시용으로 과거 -> 최신 순으로 뒤집는다.
        List<ChatMessageResponse> messages = page.stream()
                .sorted(Comparator.comparing(ChatMessage::getMessageSequence))
                .map(m -> toResponse(m, attachmentsByMessageId.getOrDefault(m.getId(), List.of())))
                .toList();

        // 다음 페이지 커서 = 이번 페이지에서 가장 과거인 메시지의 sequence (= page의 첫 원소, asc 정렬 기준)
        Long nextCursor = hasNext && !page.isEmpty()
                ? page.get(page.size() - 1).getMessageSequence()
                : null;

        return new ChatMessageListResponse(messages, hasNext, nextCursor);
    }

    private int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private ChatMessageResponse toResponse(ChatMessage chatMessage, List<ChatAttachment> attachments) {
        var sender = chatMessage.getProjectMember();
        List<ChatMessageResponse.ChatMessageAttachmentResponse> attachmentResponses =
                chatAttachmentResponseMapper.toResponses(attachments);
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getChatRoom().getId(),
                chatMessage.getMessageSequence(),
                sender.getId(),
                sender.getAnNickname(),
                sender.getUser().getProfilePreset(),
                chatMessage.getMessage(),
                attachmentResponses,
                TimeUtil.toInstant(chatMessage.getCreatedAt())
        );
    }
}