package com.plog.domain.chat.service;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageAppender {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ChatMessage append(Long chatRoomId, Long projectMemberId, String message) {
        ChatRoom room = chatRoomRepository.findByIdForMessageAppend(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        ProjectMember member = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트 멤버를 찾을 수 없습니다."));
        validateActiveRoomMember(room, member);

        long messageSequence = room.issueNextMessageSequence();
        return chatMessageRepository.save(
                ChatMessage.create(room, member, message, messageSequence)
        );
    }

    private void validateActiveRoomMember(ChatRoom room, ProjectMember member) {
        Long roomProjectId = room.getProject().getId();
        Long memberProjectId = member.getProject().getId();
        if (!roomProjectId.equals(memberProjectId) || member.getStatus() != MemberStatus.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE 채팅방 멤버만 메시지를 기록할 수 있습니다.");
        }
    }
}
