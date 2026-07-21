package com.plog.domain.chat.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChatChannelResponseSchemaTest {

    @Test
    void carriesTheLatestMessageTimeAsAnAbsoluteInstant() {
        RecordComponent latestMessageAt = Arrays.stream(ChatChannelResponse.class.getRecordComponents())
                .filter(component -> component.getName().equals("latestMessageAt"))
                .findFirst()
                .orElseThrow();

        assertThat(latestMessageAt.getType()).isEqualTo(Instant.class);
    }

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
