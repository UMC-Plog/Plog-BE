package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.chat.dto.response.ChatAttachmentMeta;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.UploadedFile;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatAttachmentDownloadServiceThumbnailTest {

    private final ChatAttachmentRepository chatAttachmentRepository =
            mock(ChatAttachmentRepository.class);
    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final ChatAttachmentDownloadService service = new ChatAttachmentDownloadService(
            chatAttachmentRepository, chatRoomRepository, fileStorageService);

    @Test
    void READY면_썸네일_키와_webp_타입을_돌려준다() {
        stubAccessibleAttachment(true);

        ChatAttachmentMeta meta = service.resolveThumbnail(3L, 7L);

        assertThat(meta.fileKey()).isEqualTo("thumbs/chats/users/7/abc/photo.png.webp");
        assertThat(meta.contentType()).isEqualTo("image/webp");
    }

    /** 원본 ETag 와 같으면 브라우저가 두 URL 의 캐시를 섞는다. */
    @Test
    void 썸네일_ETag는_원본과_다르다() {
        stubAccessibleAttachment(true);

        assertThat(service.resolveThumbnail(3L, 7L).eTag()).isEqualTo("\"11-t\"");
    }

    @Test
    void READY가_아니면_404다() {
        stubAccessibleAttachment(false);

        assertThatThrownBy(() -> service.resolveThumbnail(3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("썸네일이 준비되지 않았습니다.");
    }

    private void stubAccessibleAttachment(boolean thumbnailReady) {
        UploadedFile file = mock(UploadedFile.class);
        given(file.getId()).willReturn(11L);
        given(file.isThumbnailReady()).willReturn(thumbnailReady);
        if (thumbnailReady) {
            given(file.getOriginalFilename()).willReturn("photo.png");
            given(file.getThumbnailKey()).willReturn("thumbs/chats/users/7/abc/photo.png.webp");
        }

        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(5L);
        ChatMessage message = mock(ChatMessage.class);
        given(message.getChatRoom()).willReturn(room);

        ChatAttachment attachment = mock(ChatAttachment.class);
        given(attachment.getUploadedFile()).willReturn(file);
        given(attachment.getChatMessage()).willReturn(message);

        given(chatAttachmentRepository.findWithFileAndRoomById(3L))
                .willReturn(Optional.of(attachment));
        given(chatRoomRepository.findAccessibleRoom(anyLong(), anyLong(), any()))
                .willReturn(Optional.of(room));
    }
}
