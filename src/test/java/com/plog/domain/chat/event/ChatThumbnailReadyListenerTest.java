package com.plog.domain.chat.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.response.ChatThumbnailReadyResponse;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.service.ChatAttachmentResponseMapper;
import com.plog.global.config.ApiProperties;
import com.plog.infrastructure.s3.ThumbnailReadyEvent;
import com.plog.infrastructure.s3.UploadedFile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class ChatThumbnailReadyListenerTest {

    private final ChatAttachmentRepository chatAttachmentRepository =
            mock(ChatAttachmentRepository.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);

    private final ChatThumbnailReadyListener listener = new ChatThumbnailReadyListener(
            chatAttachmentRepository,
            new ChatAttachmentResponseMapper(new ApiProperties("https://api.umc-plog.site")),
            messagingTemplate);

    @Test
    void 방_전용_첨부_목적지로_썸네일_URL을_보낸다() {
        // mock 생성·스텁을 given() 인자 안에서 하면 UnfinishedStubbingException 이다.
        // 반드시 지역변수로 밖에서 만든다.
        ChatAttachment attachment = readyAttachment();
        given(chatAttachmentRepository.findWithFileAndRoomByUploadedFileId(11L))
                .willReturn(Optional.of(attachment));

        listener.onThumbnailReady(new ThumbnailReadyEvent(11L));

        ArgumentCaptor<String> destination = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatThumbnailReadyResponse> payload =
                ArgumentCaptor.forClass(ChatThumbnailReadyResponse.class);
        verify(messagingTemplate).convertAndSend(destination.capture(), payload.capture());

        assertThat(destination.getValue()).isEqualTo("/topic/chat-rooms/5/attachments");
        assertThat(payload.getValue().chatAttachmentId()).isEqualTo(3L);
        assertThat(payload.getValue().thumbnailUrl())
                .isEqualTo("https://api.umc-plog.site/api/chat-attachments/3/thumb");
    }

    /** 채팅이 아닌 도메인의 파일(향후 POST/TASK 확장)이면 보낼 곳이 없다. */
    @Test
    void 채팅_첨부가_아니면_아무것도_보내지_않는다() {
        given(chatAttachmentRepository.findWithFileAndRoomByUploadedFileId(11L))
                .willReturn(Optional.empty());

        listener.onThumbnailReady(new ThumbnailReadyEvent(11L));

        verifyNoInteractions(messagingTemplate);
    }

    private ChatAttachment readyAttachment() {
        UploadedFile file = mock(UploadedFile.class);
        given(file.isThumbnailReady()).willReturn(true);

        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(5L);
        ChatMessage message = mock(ChatMessage.class);
        given(message.getChatRoom()).willReturn(room);

        ChatAttachment attachment = mock(ChatAttachment.class);
        given(attachment.getId()).willReturn(3L);
        given(attachment.getUploadedFile()).willReturn(file);
        given(attachment.getChatMessage()).willReturn(message);
        return attachment;
    }
}
