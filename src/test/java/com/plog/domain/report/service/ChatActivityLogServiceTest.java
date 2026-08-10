package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatActivityLogServiceTest {

    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    @Test
    void 채팅_이벤트를_참조정보만_가진_활동로그로_수집한다() {
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        ProjectMember member = ProjectMember.builder().id(7L).build();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 18, 10, 0);
        when(message.getProjectMember()).thenReturn(member);
        when(message.getCreatedAt()).thenReturn(occurredAt);
        when(chatMessageRepository.findWithRoomAndSenderById(15L)).thenReturn(Optional.of(message));

        new ChatActivityLogService(activityLogRepository, chatMessageRepository).collectMessageSaved(15L);

        ArgumentCaptor<ReportActivityLog> captor = ArgumentCaptor.forClass(ReportActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        ReportActivityLog saved = captor.getValue();
        assertThat(saved.getSourceDomain()).isEqualTo(SourceDomain.CHAT);
        assertThat(saved.getSourceRefId()).isEqualTo("chat:15");
        assertThat(saved.getProjectMember()).isSameAs(member);
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getMetadata()).doesNotContain("저장되면 안 되는 채팅 원문");
    }

    @Test
    void 같은_채팅_이벤트가_재발행되면_중복_저장하지_않는다() {
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.CHAT, "chat:15"))
                .thenReturn(true);

        new ChatActivityLogService(activityLogRepository, chatMessageRepository).collectMessageSaved(15L);

        verify(chatMessageRepository, never()).findWithRoomAndSenderById(15L);
        verify(activityLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
