package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest.ChatMessageAttachmentRequest;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.event.ChatRoomSummaryUpdatedEvent;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.notification.event.ChatMentionEvent;
import com.plog.domain.notification.event.ChatMessageNotificationEvent;
import com.plog.domain.user.entity.User;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.UploadedFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void 신규_메시지에_clientMessageId가_저장된다() {
        when(member.getProject()).thenReturn(project);

        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-6"))
                .thenReturn(Optional.empty());
        when(room.issueNextMessageSequence()).thenReturn(1L);
        ChatMessage savedMessage = mock(ChatMessage.class);
        when(savedMessage.getId()).thenReturn(502L);
        when(chatMessageRepository.save(any())).thenReturn(savedMessage);

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-6", "hi", List.of());

        // clientMessageId가 엔티티에 실리지 않으면 다음 재전송에서 멱등 조회가 영원히 실패한다.
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getClientMessageId()).isEqualTo("client-6");
    }

    @Test
    void 신규_메시지의_멘션_이벤트는_한_번만_발행된다() {
        when(member.getProject()).thenReturn(project);

        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-4"))
                .thenReturn(Optional.empty());
        when(room.issueNextMessageSequence()).thenReturn(1L);
        ChatMessage savedMessage = mock(ChatMessage.class);
        when(savedMessage.getId()).thenReturn(501L);
        when(chatMessageRepository.save(any())).thenReturn(savedMessage);

        User mentionedUser = mock(User.class);
        when(mentionedUser.getId()).thenReturn(30L);
        ProjectMember mentioned = mock(ProjectMember.class);
        when(mentioned.getId()).thenReturn(300L);
        when(mentioned.getUser()).thenReturn(mentionedUser);

        User targetUser = mock(User.class);
        when(targetUser.getId()).thenReturn(40L);
        ProjectMember target = mock(ProjectMember.class);
        when(target.getId()).thenReturn(400L);
        when(target.getUser()).thenReturn(targetUser);

        when(projectMemberRepository.findActiveMembersByProjectIdAndNicknameIn(
                PROJECT_ID, MemberStatus.ACTIVE, java.util.Set.of("지현")))
                .thenReturn(List.of(mentioned));
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(member, mentioned, target));

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-4", "@지현 확인 부탁", List.of());

        // publishEvent는 이제 4번 호출된다: ChatMessageSavedEvent, ChatMentionEvent,
        // ChatMessageNotificationEvent, ChatRoomSummaryUpdatedEvent. 타입별로 걸러서 검증한다.
        ArgumentCaptor<Object> allEvents = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(4)).publishEvent(allEvents.capture());

        ChatMentionEvent mentionEvent = allEvents.getAllValues().stream()
                .filter(ChatMentionEvent.class::isInstance).map(ChatMentionEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(mentionEvent.projectId()).isEqualTo(PROJECT_ID);
        assertThat(mentionEvent.roomId()).isEqualTo(ROOM_ID);
        assertThat(mentionEvent.chatId()).isEqualTo(501L);

        ChatMessageNotificationEvent notificationEvent = allEvents.getAllValues().stream()
                .filter(ChatMessageNotificationEvent.class::isInstance).map(ChatMessageNotificationEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(notificationEvent.targetMemberIds()).containsExactly(400L);

        // 채팅방 목록 갱신은 멘션 여부와 무관하게 발신자만 제외한 전원(mentioned, target 둘 다)한테 간다.
        ChatRoomSummaryUpdatedEvent summaryEvent = allEvents.getAllValues().stream()
                .filter(ChatRoomSummaryUpdatedEvent.class::isInstance).map(ChatRoomSummaryUpdatedEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(summaryEvent.roomId()).isEqualTo(ROOM_ID);
        assertThat(summaryEvent.targetUserIds()).containsExactlyInAnyOrder(30L, 40L);
        assertThat(summaryEvent.latestMessage()).isEqualTo("@지현 확인 부탁");
    }

    @Test
    void 신규_메시지는_일반_채팅_알림_이벤트를_한_번_발행한다() {
        when(member.getProject()).thenReturn(project);
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-7"))
                .thenReturn(Optional.empty());
        when(room.issueNextMessageSequence()).thenReturn(1L);
        ChatMessage savedMessage = mock(ChatMessage.class);
        when(savedMessage.getId()).thenReturn(507L);
        when(chatMessageRepository.save(any())).thenReturn(savedMessage);
        User targetUser = mock(User.class);
        when(targetUser.getId()).thenReturn(40L);
        ProjectMember target = mock(ProjectMember.class);
        when(target.getId()).thenReturn(300L);
        when(target.getUser()).thenReturn(targetUser);
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(member, target));

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-7", "안녕하세요", List.of());

        // publishEvent 3번: ChatMessageSavedEvent, ChatMessageNotificationEvent, ChatRoomSummaryUpdatedEvent
        ArgumentCaptor<Object> allEvents = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(3)).publishEvent(allEvents.capture());

        ChatMessageNotificationEvent notificationEvent = allEvents.getAllValues().stream()
                .filter(ChatMessageNotificationEvent.class::isInstance).map(ChatMessageNotificationEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(notificationEvent.chatId()).isEqualTo(507L);
        assertThat(notificationEvent.targetMemberIds()).containsExactly(300L);

        ChatRoomSummaryUpdatedEvent summaryEvent = allEvents.getAllValues().stream()
                .filter(ChatRoomSummaryUpdatedEvent.class::isInstance).map(ChatRoomSummaryUpdatedEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(summaryEvent.roomId()).isEqualTo(ROOM_ID);
        assertThat(summaryEvent.targetUserIds()).containsExactly(40L);
        assertThat(summaryEvent.latestMessage()).isEqualTo("안녕하세요");
    }

    @Test
    void 텍스트_없이_첨부만_있으면_목록_미리보기는_파일명이_된다() {
        when(member.getProject()).thenReturn(project);
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-8"))
                .thenReturn(Optional.empty());
        when(room.issueNextMessageSequence()).thenReturn(1L);
        ChatMessage savedMessage = mock(ChatMessage.class);
        when(savedMessage.getId()).thenReturn(508L);
        when(chatMessageRepository.save(any())).thenReturn(savedMessage);

        UploadedFile confirmed = mock(UploadedFile.class);
        when(attachmentPolicy.confirmFileAttachment(any(), any(), any(), any(), any(), any()))
                .thenReturn(confirmed);

        User targetUser = mock(User.class);
        when(targetUser.getId()).thenReturn(40L);
        ProjectMember target = mock(ProjectMember.class);
        when(target.getId()).thenReturn(300L);
        when(target.getUser()).thenReturn(targetUser);
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(PROJECT_ID, MemberStatus.ACTIVE))
                .thenReturn(List.of(member, target));

        List<ChatMessageAttachmentRequest> attachments = List.of(
                new ChatMessageAttachmentRequest("key1", "회의록.pdf", 100L),
                new ChatMessageAttachmentRequest("key2", "녹취록.mp3", 200L));

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-8", null, attachments);

        ArgumentCaptor<Object> allEvents = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(3)).publishEvent(allEvents.capture());

        ChatRoomSummaryUpdatedEvent summaryEvent = allEvents.getAllValues().stream()
                .filter(ChatRoomSummaryUpdatedEvent.class::isInstance).map(ChatRoomSummaryUpdatedEvent.class::cast)
                .findFirst().orElseThrow();
        assertThat(summaryEvent.latestMessage()).isEqualTo("회의록.pdf 외 1개");
    }

    @Test
    void 멱등_히트면_멘션_이벤트를_발행하지_않는다() {
        ChatMessage existing = mock(ChatMessage.class);
        when(existing.getId()).thenReturn(999L);
        when(chatMessageRepository.findByChatRoomIdAndProjectMemberIdAndClientMessageId(ROOM_ID, 200L, "client-5"))
                .thenReturn(Optional.of(existing));

        chatMessageAppender.appendByUser(ROOM_ID, USER_ID, "client-5", "@지현 확인 부탁", List.of());

        verify(eventPublisher, never()).publishEvent(any(ChatMentionEvent.class));
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