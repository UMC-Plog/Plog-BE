package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.global.config.ApiProperties;
import com.plog.infrastructure.s3.ThumbnailStatus;
import com.plog.infrastructure.s3.UploadedFile;
import org.junit.jupiter.api.Test;

class ChatAttachmentResponseMapperTest {

    private final ChatAttachmentResponseMapper mapper =
            new ChatAttachmentResponseMapper(
                    new ApiProperties("https://api.umc-plog.site"));

    @Test
    void 프록시_절대_URL을_만든다() {
        ChatMessageResponse.ChatMessageAttachmentResponse response =
                mapper.toResponse(attachmentWith(ThumbnailStatus.NONE));

        assertThat(response.fileUrl())
                .isEqualTo("https://api.umc-plog.site/api/chat-attachments/3");
    }

    @Test
    void READY면_썸네일_URL을_채우고_pending은_false다() {
        ChatMessageResponse.ChatMessageAttachmentResponse response =
                mapper.toResponse(attachmentWith(ThumbnailStatus.READY));

        assertThat(response.thumbnailUrl())
                .isEqualTo("https://api.umc-plog.site/api/chat-attachments/3/thumb");
        assertThat(response.thumbnailPending()).isFalse();
    }

    /**
     * 이 조합이 이 기능의 핵심이다. pending 이 true 여야 프론트가 원본을 요청하지 않고
     * 스켈레톤을 띄운다. false 로 새면 브로드캐스트 시점 원본 팬아웃이 그대로 돌아온다.
     */
    @Test
    void PENDING이면_URL은_null이고_pending은_true다() {
        ChatMessageResponse.ChatMessageAttachmentResponse response =
                mapper.toResponse(attachmentWith(ThumbnailStatus.PENDING));

        assertThat(response.thumbnailUrl()).isNull();
        assertThat(response.thumbnailPending()).isTrue();
    }

    @Test
    void NONE이나_FAILED면_둘_다_원본_표시로_떨어진다() {
        for (ThumbnailStatus status : new ThumbnailStatus[]{
                ThumbnailStatus.NONE, ThumbnailStatus.FAILED}) {
            ChatMessageResponse.ChatMessageAttachmentResponse response =
                    mapper.toResponse(attachmentWith(status));

            assertThat(response.thumbnailUrl()).as("%s", status).isNull();
            assertThat(response.thumbnailPending()).as("%s", status).isFalse();
        }
    }

    /**
     * 계약 고정이 목적이므로 필드가 응답에서 <b>사라지면</b> 안 된다. 전역 Jackson 설정이
     * NON_NULL 로 바뀌면 프론트의 {@code thumbnailUrl ?? fileUrl} 은 여전히 동작하지만
     * "나중에 값이 채워진다"는 계약이 응답에서 보이지 않게 된다.
     */
    @Test
    void thumbnailUrl은_null로_직렬화된다() throws Exception {
        String json = new ObjectMapper()
                .writeValueAsString(mapper.toResponse(attachmentWith(ThumbnailStatus.NONE)));

        assertThat(json).contains("\"thumbnailUrl\":null");
        assertThat(json).contains("\"thumbnailPending\":false");
    }

    @Test
    void 파일명과_크기는_레지스트리에서_가져온다() {
        ChatMessageResponse.ChatMessageAttachmentResponse response =
                mapper.toResponse(attachmentWith(ThumbnailStatus.NONE));

        assertThat(response.chatAttachmentId()).isEqualTo(3L);
        assertThat(response.fileName()).isEqualTo("photo.png");
        assertThat(response.fileSize()).isEqualTo(2048L);
    }

    @Test
    void baseUrl_끝의_슬래시가_있어도_이중_슬래시가_생기지_않는다() {
        ChatAttachmentResponseMapper trailing = new ChatAttachmentResponseMapper(
                new ApiProperties("https://api.umc-plog.site/"));

        assertThat(trailing.toResponse(attachmentWith(ThumbnailStatus.NONE)).fileUrl())
                .isEqualTo("https://api.umc-plog.site/api/chat-attachments/3");
    }

    private ChatAttachment attachmentWith(ThumbnailStatus status) {
        // mock 생성·스텁을 given() 인자 안에서 하면 UnfinishedStubbingException 이다.
        // 반드시 지역변수로 밖에서 만든다.
        UploadedFile file = mock(UploadedFile.class);
        given(file.getOriginalFilename()).willReturn("photo.png");
        given(file.getSize()).willReturn(2048L);
        given(file.isThumbnailReady()).willReturn(status == ThumbnailStatus.READY);
        given(file.isThumbnailPending()).willReturn(status == ThumbnailStatus.PENDING);

        ChatAttachment attachment = mock(ChatAttachment.class);
        given(attachment.getId()).willReturn(3L);
        given(attachment.getUploadedFile()).willReturn(file);
        return attachment;
    }
}
