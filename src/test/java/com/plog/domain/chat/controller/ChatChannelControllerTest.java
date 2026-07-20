package com.plog.domain.chat.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.chat.dto.response.ChatChannelListResponse;
import com.plog.domain.chat.dto.response.ChatChannelListResponse.Channel;
import com.plog.domain.chat.dto.response.ChatChannelListResponse.PageInfo;
import com.plog.domain.chat.service.ChatChannelListService;
import com.plog.global.security.jwt.JwtProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatChannelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatChannelListService service;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsChannelsForTheRawLongPrincipalWithDefaultPaging() throws Exception {
        authenticate(1L);
        ChatChannelListResponse response = new ChatChannelListResponse(
                List.of(new Channel(
                        10L,
                        "Plog",
                        20L,
                        "latest",
                        LocalDateTime.of(2026, 7, 20, 12, 0),
                        true,
                        2L
                )),
                new PageInfo(0, 20, 1, 1, false)
        );
        given(service.getChannels(1L, 0, 20)).willReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHAT001"))
                .andExpect(jsonPath("$.result.content[0].projectId").value(10L))
                .andExpect(jsonPath("$.result.content[0].roomId").value(20L))
                .andExpect(jsonPath("$.result.content[0].hasUnreadMessage").value(true))
                .andExpect(jsonPath("$.result.content[0].unreadMessageCount").value(2L))
                .andExpect(jsonPath("$.result.pageInfo.page").value(0))
                .andExpect(jsonPath("$.result.pageInfo.hasNext").value(false));
    }

    @Test
    void includesNullLatestMessageFieldsForAChannelWithoutMessages() throws Exception {
        authenticate(1L);
        ChatChannelListResponse response = new ChatChannelListResponse(
                List.of(new Channel(10L, "Plog", 20L, null, null, false, 0L)),
                new PageInfo(0, 20, 1, 1, false)
        );
        given(service.getChannels(1L, 0, 20)).willReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content[0].latestMessage").value(nullValue()))
                .andExpect(jsonPath("$.result.content[0].latestMessageAt").value(nullValue()))
                .andExpect(jsonPath("$.result.content[0].hasUnreadMessage").value(false))
                .andExpect(jsonPath("$.result.content[0].unreadMessageCount").value(0L));
    }

    @Test
    void rejectsAnInvalidPageBeforeCallingTheService() throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/v1/dashboard/channels").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void rejectsAnInvalidSizeBeforeCallingTheService(String size) throws Exception {
        authenticate(1L);

        mockMvc.perform(get("/api/v1/dashboard/channels").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(service);
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
