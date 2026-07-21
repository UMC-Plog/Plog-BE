package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.response.ChatChannelListResponse;
import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ChatChannelListServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatChannelSummary summary;

    @Mock
    private ChatChannelParticipantService participantService;

    private ChatChannelListService service;

    @BeforeEach
    void setUp() {
        service = new ChatChannelListService(chatRoomRepository, participantService);
    }

    @Test
    void mapsChannelsAndNestedPageInfo() {
        LocalDateTime latestMessageAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        given(summary.getProjectId()).willReturn(10L);
        given(summary.getProjectName()).willReturn("Plog");
        given(summary.getRoomId()).willReturn(20L);
        given(summary.getLatestMessage()).willReturn("latest");
        given(summary.getLatestMessageAt()).willReturn(latestMessageAt);
        given(summary.getUnreadMessageCount()).willReturn(2L);
        given(chatRoomRepository.findChannelPage(
                1L, MemberStatus.ACTIVE, PageRequest.of(0, 2)
        )).willReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 2), 3));
        given(participantService.getParticipantsByProjectIds(List.of(10L)))
                .willReturn(Map.of(10L, List.of(
                        new ChatChannelParticipantResponse(
                                1L,
                                "바나",
                                "https://image.test/1.png"
                        ),
                        new ChatChannelParticipantResponse(2L, "팀원", null)
                )));

        ChatChannelListResponse response = service.getChannels(1L, 0, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().projectId()).isEqualTo(10L);
        assertThat(response.content().getFirst().latestMessageAt()).isEqualTo(latestMessageAt);
        assertThat(response.content().getFirst().hasUnreadMessage()).isTrue();
        assertThat(response.content().getFirst().unreadMessageCount()).isEqualTo(2L);
        assertThat(response.content().getFirst().participants()).containsExactly(
                new ChatChannelParticipantResponse(1L, "바나", "https://image.test/1.png"),
                new ChatChannelParticipantResponse(2L, "팀원", null)
        );
        assertThat(response.pageInfo().page()).isZero();
        assertThat(response.pageInfo().size()).isEqualTo(2);
        assertThat(response.pageInfo().totalElements()).isEqualTo(3);
        assertThat(response.pageInfo().totalPages()).isEqualTo(2);
        assertThat(response.pageInfo().hasNext()).isTrue();
    }

    @Test
    void rejectsANullPrincipalBeforeQueryingChannels() {
        assertThatThrownBy(() -> service.getChannels(null, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verifyNoInteractions(chatRoomRepository, participantService);
    }
}
