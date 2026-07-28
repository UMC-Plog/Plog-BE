package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.global.config.MediaProperties;
import com.plog.infrastructure.s3.UploadedFile;
import org.junit.jupiter.api.Test;

class ChatAttachmentResponseMapperTest {

    private final ChatAttachmentResponseMapper mapper =
            new ChatAttachmentResponseMapper(
                    new MediaProperties("https://api.umc-plog.site", "None"));

    @Test
    void 프록시_절대_URL을_만든다() {
        ChatMessageResponse.ChatMessageAttachmentResponse response = mapper.toResponse(attachment());

        assertThat(response.fileUrl())
                .isEqualTo("https://api.umc-plog.site/api/chat-attachments/3");
    }

    @Test
    void 썸네일은_아직_없어서_항상_null이다() {
        assertThat(mapper.toResponse(attachment()).thumbnailUrl()).isNull();
    }

    /**
     * 계약 고정이 목적이므로 필드가 응답에서 <b>사라지면</b> 안 된다. 전역 Jackson 설정이
     * NON_NULL 로 바뀌면 프론트의 {@code thumbnailUrl ?? fileUrl} 은 여전히 동작하지만
     * "나중에 값이 채워진다"는 계약이 응답에서 보이지 않게 된다.
     */
    @Test
    void thumbnailUrl은_null로_직렬화된다() throws Exception {
        String json = new ObjectMapper().writeValueAsString(mapper.toResponse(attachment()));

        assertThat(json).contains("\"thumbnailUrl\":null");
    }

    @Test
    void 파일명과_크기는_레지스트리에서_가져온다() {
        ChatMessageResponse.ChatMessageAttachmentResponse response = mapper.toResponse(attachment());

        assertThat(response.chatAttachmentId()).isEqualTo(3L);
        assertThat(response.fileName()).isEqualTo("photo.png");
        assertThat(response.fileSize()).isEqualTo(2048L);
    }

    @Test
    void baseUrl_끝의_슬래시가_있어도_이중_슬래시가_생기지_않는다() {
        ChatAttachmentResponseMapper trailing = new ChatAttachmentResponseMapper(
                new MediaProperties("https://api.umc-plog.site/", "None"));

        assertThat(trailing.toResponse(attachment()).fileUrl())
                .isEqualTo("https://api.umc-plog.site/api/chat-attachments/3");
    }

    private ChatAttachment attachment() {
        UploadedFile file = mock(UploadedFile.class);
        given(file.getOriginalFilename()).willReturn("photo.png");
        given(file.getSize()).willReturn(2048L);

        ChatAttachment attachment = mock(ChatAttachment.class);
        given(attachment.getId()).willReturn(3L);
        given(attachment.getUploadedFile()).willReturn(file);
        return attachment;
    }
}
