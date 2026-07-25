package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatReadResponse;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.entity.ChatRoomReadCursor;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomReadCursorRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatRoomUnreadCount;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ChatRoomReadCursorRepository chatRoomReadCursorRepository;

    @Transactional
    public ChatReadResponse markAsRead(Long roomId, Long userId, Long lastReadMessageId) {
        ChatRoom room = chatRoomRepository.findAccessibleRoom(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

        // advance()는 조건 불충족 시 조용히 0건 갱신되어 "역행 요청"과 "존재하지 않는 메시지"를
        // 구분하지 못한다. 그래서 대상 메시지가 이 방 소속인지 먼저 검증한다.
        chatMessageRepository.findByIdAndChatRoomId(lastReadMessageId, roomId)
                .orElseThrow(() -> new ApiException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));

        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(room.getProject().getId(), userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

        // 최초 읽음 처리인 경우 커서가 없으므로 먼저 만들어 둔다.
        // ON CONFLICT DO NOTHING이라 동시에 여러 요청이 들어와도 예외 없이 안전하다.
        chatRoomReadCursorRepository.createIfAbsent(roomId, member.getId());

        // 과거/동일 sequence 재요청이면 이 UPDATE는 0건 갱신되고 그대로 멱등하게 무시된다.
        chatRoomReadCursorRepository.advance(roomId, userId, lastReadMessageId, MemberStatus.ACTIVE.name());

        ChatRoomReadCursor cursor = chatRoomReadCursorRepository
                .findAccessibleCursor(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

        long unreadCount = chatRoomReadCursorRepository.findUnreadCount(roomId, userId, MemberStatus.ACTIVE)
                .map(ChatRoomUnreadCount::getUnreadCount)
                .orElse(0L);

        return new ChatReadResponse(roomId, cursor.getLastReadMessageSequence(), unreadCount);
    }
}