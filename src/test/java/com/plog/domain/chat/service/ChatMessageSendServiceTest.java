package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest.ChatMessageAttachmentRequest;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 첨부 검증은 이 서비스의 책임이 아니다 — 멱등 히트 판정 이후에 해야 하므로
 * ChatMessageAppender 안쪽으로 옮겼다. 여기서는 메시지 본문·clientMessageId 검증과
 * 위임만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageSendServiceTest {

    @Mock private ChatMessageAppender chatMessageAppender;

    @InjectMocks
    private ChatMessageSendService chatMessageSendService;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 10L;

    @Test
    void 텍스트만_전송하면_정상_처리() {
        chatMessageSendService.send(ROOM_ID, USER_ID, "client-1", "안녕하세요", null);

        verify(chatMessageAppender).appendByUser(ROOM_ID, USER_ID, "client-1", "안녕하세요", List.of());
    }

    @Test
    void 첨부만_전송해도_그대로_위임한다() {
        ChatMessageAttachmentRequest attachment =
                new ChatMessageAttachmentRequest("key1", "image.png", 1000L);

        chatMessageSendService.send(ROOM_ID, USER_ID, "client-2", null, List.of(attachment));

        verify(chatMessageAppender)
                .appendByUser(ROOM_ID, USER_ID, "client-2", null, List.of(attachment));
    }

    @Test
    void 텍스트도_첨부도_없으면_예외() {
        assertThatThrownBy(() -> chatMessageSendService.send(ROOM_ID, USER_ID, "client-3", "  ", List.of()))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.EMPTY_MESSAGE_CONTENT);
    }

    @Test
    void clientMessageId가_없으면_예외() {
        assertThatThrownBy(() -> chatMessageSendService.send(ROOM_ID, USER_ID, "", "안녕", null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.MISSING_CLIENT_MESSAGE_ID);
    }

    @Test
    void clientMessageId가_64자를_초과하면_예외() {
        String tooLong = "a".repeat(65);

        assertThatThrownBy(() -> chatMessageSendService.send(ROOM_ID, USER_ID, tooLong, "안녕", null))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.INVALID_CLIENT_MESSAGE_ID);
    }
}
