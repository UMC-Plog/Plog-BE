package com.plog.domain.chat.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatChannelResponseSchemaTest {

    @Test
    void usesDistinctComponentNamesForListAndSearchSchemas() {
        Set<String> componentNames = Set.of(
                ChatChannelListResponse.Channel.class.getSimpleName(),
                ChatChannelListResponse.PageInfo.class.getSimpleName(),
                ChatChannelSearchResponse.SearchChannel.class.getSimpleName(),
                ChatChannelSearchResponse.SearchPageInfo.class.getSimpleName()
        );

        assertThat(componentNames).hasSize(4);
    }
}
