package com.plog.domain.chat.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChatChannelResponseSchemaTest {

    @Test
    void usesOneSharedChannelCardSchemaForListAndSearch() {
        String[] componentNames = Arrays.stream(ChatChannelResponse.class.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);

        assertThat(componentNames).containsExactly(
                "projectId",
                "projectName",
                "roomId",
                "latestMessage",
                "latestMessageAt",
                "hasUnreadMessage",
                "unreadMessageCount",
                "participants"
        );
    }
}
