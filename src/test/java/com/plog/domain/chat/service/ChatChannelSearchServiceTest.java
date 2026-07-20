package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.response.ChatChannelSearchResponse;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ChatChannelSearchServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatChannelSummary summary;

    private ChatChannelSearchService service;

    @BeforeEach
    void setUp() {
        service = new ChatChannelSearchService(chatRoomRepository);
    }

    @Test
    void normalizesTheKeywordAndMapsTheSearchResponse() {
        LocalDateTime latestMessageAt = LocalDateTime.of(2026, 7, 20, 12, 0);
        given(summary.getProjectId()).willReturn(10L);
        given(summary.getProjectName()).willReturn("PLOG API");
        given(summary.getRoomId()).willReturn(20L);
        given(summary.getLatestMessageAt()).willReturn(latestMessageAt);
        given(summary.getUnreadMessageCount()).willReturn(2L);
        given(chatRoomRepository.findChannelPageByProjectName(
                1L,
                MemberStatus.ACTIVE,
                "%plog!%!_!!%",
                PageRequest.of(0, 2)
        )).willReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 2), 3));

        ChatChannelSearchResponse response = service.search(1L, "  PLOG%_!  ", 0, 2);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().projectId()).isEqualTo(10L);
        assertThat(response.content().getFirst().projectName()).isEqualTo("PLOG API");
        assertThat(response.content().getFirst().roomId()).isEqualTo(20L);
        assertThat(response.content().getFirst().latestMessageAt()).isEqualTo(latestMessageAt);
        assertThat(response.content().getFirst().hasUnreadMessage()).isTrue();
        assertThat(response.pageInfo().totalElements()).isEqualTo(3);
        assertThat(response.pageInfo().totalPages()).isEqualTo(2);
        assertThat(response.pageInfo().hasNext()).isTrue();
        verify(chatRoomRepository).findChannelPageByProjectName(
                1L,
                MemberStatus.ACTIVE,
                "%plog!%!_!!%",
                PageRequest.of(0, 2)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidKeywords")
    void rejectsAnInvalidKeyword(String keyword) {
        assertThatThrownBy(() -> service.search(1L, keyword, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.INVALID_SEARCH_KEYWORD));

        verifyNoInteractions(chatRoomRepository);
    }

    @Test
    void rejectsANullPrincipalBeforeSearching() {
        assertThatThrownBy(() -> service.search(null, "plog", 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verifyNoInteractions(chatRoomRepository);
    }

    private static Stream<Arguments> invalidKeywords() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("123456789012345678901")
        );
    }
}
