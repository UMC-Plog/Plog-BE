package com.plog.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.chat.controller.docs.ChatChannelControllerDoc;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;

class ChatChannelControllerDocTest {

    @Test
    void keepsOpenApiDocumentationInTheControllerDocInterface() throws Exception {
        assertThat(ChatChannelController.class.getInterfaces())
                .contains(ChatChannelControllerDoc.class);
        assertThat(ChatChannelControllerDoc.class.getMethod(
                "getChannels",
                Long.class,
                int.class,
                int.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(ChatChannelControllerDoc.class.getMethod(
                "searchChannels",
                Long.class,
                String.class,
                int.class,
                int.class
        ).getAnnotation(Operation.class)).isNotNull();
        assertThat(ChatChannelController.class.getMethod(
                "getChannels",
                Long.class,
                int.class,
                int.class
        ).getDeclaredAnnotation(Operation.class)).isNull();
    }
}
