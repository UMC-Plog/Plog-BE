package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest.ChatMessageAttachmentRequest;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.UploadedFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ChatMessageAppenderTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatAttachmentRepository chatAttachmentRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query query;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AttachmentPolicy attachmentPolicy;

    @InjectMocks
    private ChatMessageAppender chatMessageAppender;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long PROJECT_ID = 100L;

    private ChatRoom room;
    private ProjectMember member;
    private Project project;

    @BeforeEach
    void setUp() {
        // 모든 테스트가 공통으로 필요한 스텁만 여기에 둔다.
        // member.getProject()처럼 특정 테스트(신규 생성 경로)에서만 쓰이는 스텁은
        // 여기 두면 멱등 히트 테스트에서 UnnecessaryStubbingException이 난다 — 해당 테스트로 옮긴다.
        project = mock(Project.class);
        when(project.getId()).thenReturn(PROJECT_ID);

        room = mock(ChatRoom.class);
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getProject()).thenReturn(project);

        member = mock(ProjectMember.class);
        when(member.getId()).thenReturn(200L);

        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(chatRoomRepository.findByIdForMessageAppend(ROOM_ID)).thenReturn(Optional.of(room));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(PROJECT_ID, USER_ID, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
    }

    @Test
    void 신규_메시지의_첨부는_확정을_거쳐_저장된다() {
        // ChatMessage.create()의 프로젝트 일치 검증을 통과하려면
        // member도 room과 "같은" project 인스턴스를 리턴해야 한다.
        when(member.getProject()).thenReturn(project);

        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-1"))
                .thenReturn(Optional.empty());
        when(room.issueNextMessageSequence()).thenReturn(1L);
        ChatMessage savedMessage = mock(ChatMessage.class);
        when(savedMessage.getId()).thenReturn(500L);
        when(chatMessageRepository.save(any())).thenReturn(savedMessage);

        UploadedFile confirmed = mock(UploadedFile.class);
        when(attachmentPolicy.confirmFileAttachment(any(), any(), any(), any(), any(), any()))
                .thenReturn(confirmed);

        ChatMessageAttachmentRequest attachment =
                new ChatMessageAttachmentRequest("key1", "a.png", 100L);

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-1", null, List.of(attachment));

        verify(chatAttachmentRepository).saveAll(any());
        verify(attachmentPolicy).confirmFileAttachment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 같은_clientMessageId_재전송은_첨부를_다시_확정하지_않는다() {
        ChatMessage existing = mock(ChatMessage.class);
        when(existing.getId()).thenReturn(999L);
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-3"))
                .thenReturn(Optional.of(existing));

        ChatMessageAttachmentRequest attachment =
                new ChatMessageAttachmentRequest("key1", "a.png", 100L);

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-3", "hi", List.of(attachment));

        // 멱등 히트면 확정을 시도하지 않는다 — 이미 CONFIRMED 라 409 가 난다.
        verifyNoInteractions(attachmentPolicy);
    }

    @Test
    void 멱등_히트면_첨부를_다시_저장하지_않는다() {
        ChatMessage existing = mock(ChatMessage.class);
        when(existing.getId()).thenReturn(999L);
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-2"))
                .thenReturn(Optional.of(existing));

        ChatMessageAttachmentRequest attachment =
                new ChatMessageAttachmentRequest("key1", "a.png", 100L);

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-2", null, List.of(attachment));

        verify(chatAttachmentRepository, never()).saveAll(any());
        verify(chatMessageRepository, never()).save(any());
    }
}