package com.plog.domain.chat.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void rejectsABlankMessageBeforePersistence() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(1L);
        ChatRoom room = mock(ChatRoom.class);
        given(room.getProject()).willReturn(project);
        ProjectMember member = mock(ProjectMember.class);
        given(member.getProject()).willReturn(project);

        assertThatThrownBy(() -> ChatMessage.create(room, member, "   ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
