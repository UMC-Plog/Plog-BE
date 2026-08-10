package com.plog.domain.report.event;

import static org.mockito.Mockito.verify;

import com.plog.domain.chat.event.ChatMessageSavedEvent;
import com.plog.domain.report.service.ChatActivityLogService;
import org.junit.jupiter.api.Test;

class ChatActivityLogListenerTest {

    @Test
    void 저장된_채팅_이벤트를_활동수집_서비스로_연결한다() {
        ChatActivityLogService service = org.mockito.Mockito.mock(ChatActivityLogService.class);

        new ChatActivityLogListener(service).onChatMessageSaved(new ChatMessageSavedEvent(15L));

        verify(service).collectMessageSaved(15L);
    }
}
