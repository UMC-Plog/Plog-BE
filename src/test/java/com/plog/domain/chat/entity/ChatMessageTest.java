package com.plog.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void allowsBlankMessageWhenAttachmentsWillBeAttachedSeparately() {
        // 첨부만 있는 메시지를 허용하기 위해 엔티티는 더 이상 blank 메시지를 거부하지 않는다.
        // "텍스트 또는 첨부 중 하나는 필수"라는 규칙은 ChatMessageSendService에서 검증한다
        // (엔티티 생성 시점엔 첨부 저장 여부를 알 수 없기 때문).
        Project project = mock(Project.class);
        given(project.getId()).willReturn(1L);
        ChatRoom room = mock(ChatRoom.class);
        given(room.getProject()).willReturn(project);
        ProjectMember member = mock(ProjectMember.class);
        given(member.getProject()).willReturn(project);

        ChatMessage chatMessage = ChatMessage.create(room, member, "   ", 1L, null);

        assertThat(chatMessage.getMessage()).isEqualTo("   ");
    }

    @Test
    void rejectsWhenRoomAndMemberBelongToDifferentProjects() {
        Project roomProject = mock(Project.class);
        given(roomProject.getId()).willReturn(1L);
        Project memberProject = mock(Project.class);
        given(memberProject.getId()).willReturn(2L);
        ChatRoom room = mock(ChatRoom.class);
        given(room.getProject()).willReturn(roomProject);
        ProjectMember member = mock(ProjectMember.class);
        given(member.getProject()).willReturn(memberProject);

        assertThatThrownBy(() -> ChatMessage.create(room, member, "안녕하세요", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMessageSequence() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(1L);
        ChatRoom room = mock(ChatRoom.class);
        given(room.getProject()).willReturn(project);
        ProjectMember member = mock(ProjectMember.class);
        given(member.getProject()).willReturn(project);

        assertThatThrownBy(() -> ChatMessage.create(room, member, "안녕하세요", 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}