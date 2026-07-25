package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.chat.dto.request.ChatMessageSendRequest.ChatMessageAttachmentRequest;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageSendServiceTest {

    @Mock private ChatMessageAppender chatMessageAppender;
    @Mock private AttachmentPolicy attachmentPolicy;

    @InjectMocks
    private ChatMessageSendService chatMessageSendService;

    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 10L;

    @Test
    void 텍스트만_전송하면_정상_처리() {
        chatMessageSendService.send(ROOM_ID, USER_ID, "client-1", "안녕하세요", null);

        verify(chatMessageAppender).appendByUser(ROOM_ID, USER_ID, "client-1", "안녕하세요", List.of());
        verify(attachmentPolicy, never()).validateFileAttachment(
                any(AttachmentUsage.class), any(Long.class), any(String.class),
                any(Long.class), any(String.class), any());
    }

    @Test
    void 첨부만_전송해도_정상_처리() {
        ChatMessageAttachmentRequest attachment =
                new ChatMessageAttachmentRequest("key1", "image.png", 1000L);

        chatMessageSendService.send(ROOM_ID, USER_ID, "client-2", null, List.of(attachment));

        verify(attachmentPolicy).validateCount(1, ChatErrorCode.TOO_MANY_CHAT_ATTACHMENTS);
        verify(attachmentPolicy).validateFileAttachment(
                eq(AttachmentUsage.CHAT), eq(USER_ID), eq("image.png"),
                eq(1000L), eq("key1"), eq(ChatErrorCode.INVALID_CHAT_ATTACHMENT));
        verify(chatMessageAppender).appendByUser(ROOM_ID, USER_ID, "client-2", null, List.of(attachment));
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

    @Test
    void 첨부_필드가_누락되면_예외() {
        ChatMessageAttachmentRequest invalid = new ChatMessageAttachmentRequest(null, "a.png", 100L);

        assertThatThrownBy(() -> chatMessageSendService.send(ROOM_ID, USER_ID, "client-4", null, List.of(invalid)))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.INVALID_CHAT_ATTACHMENT);
    }

    @Test
    void 첨부가_11개면_예외() {
        List<ChatMessageAttachmentRequest> tooMany = IntStream.range(0, 11)
                .mapToObj(i -> new ChatMessageAttachmentRequest("key" + i, "f" + i + ".png", 100L))
                .toList();
        doThrow(new ApiException(ChatErrorCode.TOO_MANY_CHAT_ATTACHMENTS))
                .when(attachmentPolicy).validateCount(anyInt(), eq(ChatErrorCode.TOO_MANY_CHAT_ATTACHMENTS));

        assertThatThrownBy(() -> chatMessageSendService.send(ROOM_ID, USER_ID, "client-5", null, tooMany))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.TOO_MANY_CHAT_ATTACHMENTS);
    }
}