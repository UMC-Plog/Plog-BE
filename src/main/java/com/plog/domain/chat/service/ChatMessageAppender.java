package com.plog.domain.chat.service;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.event.ChatMessageSavedEvent;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.QueryTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageAppender {

    private static final String LOCK_TIMEOUT_SQL = "SET LOCAL lock_timeout = '3s'";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    // PostgreSQL은 FOR UPDATE에 대기시간을 직접 못 줘서(NOWAIT 아니면 무기한 대기),
    // 트랜잭션 세션에 lock_timeout을 걸어 잠금 대기 자체를 bound 시킨다.
    // SET LOCAL이라 이 트랜잭션 종료(커밋/롤백) 시 자동 해제됨.
    private ChatRoom lockRoomForAppend(Long chatRoomId) {
        try {
            entityManager.createNativeQuery(LOCK_TIMEOUT_SQL).executeUpdate();
            return chatRoomRepository.findByIdForMessageAppend(chatRoomId)
                    .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));
        } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new ApiException(ChatErrorCode.CHAT_ROOM_LOCK_TIMEOUT, exception);
        }
    }

    // clientMessageId가 없어 멱등 대상이 아니고, 브로드캐스트 이벤트도 발행하지 않는다.
    /** 실시간 전파가 필요한 호출부라면 appendByUser로 옮기거나 별도 이벤트 발행이 필요함. **/
    @Transactional
    public ChatMessage append(Long chatRoomId, Long projectMemberId, String message) {
        ChatRoom room = lockRoomForAppend(chatRoomId);
        ProjectMember member = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));
        validateActiveRoomMember(room, member);

        long messageSequence = room.issueNextMessageSequence();
        return chatMessageRepository.save(
                ChatMessage.create(room, member, message, messageSequence, null)
        );
    }

    private void validateActiveRoomMember(ChatRoom room, ProjectMember member) {
        Long roomProjectId = room.getProject().getId();
        Long memberProjectId = member.getProject().getId();
        if (!roomProjectId.equals(memberProjectId) || member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE 채팅방 멤버만 메시지를 기록할 수 있습니다.");
        }
    }

    /** WebSocket 경로 전용: STOMP Principal의 userId로 프로젝트 멤버를 찾아 메시지를 남긴다.
     *  clientMessageId로 멱등 처리하고, 커밋 후 브로드캐스트 이벤트를 발행한다. */
    @Transactional
    public ChatMessage appendByUser(Long chatRoomId, Long userId, String clientMessageId, String message) {
        ChatRoom room = lockRoomForAppend(chatRoomId);
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(room.getProject().getId(), userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

        ChatMessage chatMessage = chatMessageRepository
                .findByChatRoomIdAndProjectMemberIdAndClientMessageId(room.getId(), member.getId(), clientMessageId)
                .orElseGet(() -> createAndSave(room, member, clientMessageId, message));

        // 커밋 후에만 발행됨(AFTER_COMMIT) — 재전송(멱등 히트)이어도 재브로드캐스트를 유도하기 위해 항상 발행
        eventPublisher.publishEvent(new ChatMessageSavedEvent(chatMessage.getId()));
        return chatMessage;
    }

    private ChatMessage createAndSave(ChatRoom room, ProjectMember member, String clientMessageId, String message) {
        long messageSequence = room.issueNextMessageSequence();
        return chatMessageRepository.save(
                ChatMessage.create(room, member, message, messageSequence, clientMessageId)
        );
    }
}
