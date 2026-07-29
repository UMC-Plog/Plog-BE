package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.global.api.error.ChatErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class UploadedFileServiceThumbnailTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    private final UploadedFileRepository repository = mock(UploadedFileRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    @Test
    void 채팅_이미지는_확정되면_PENDING이_되고_이벤트가_발행된다() {
        UploadedFile file = stubConfirmable("image/png", AttachmentUsage.CHAT, "photo.png");

        service(true).confirmNew(AttachmentUsage.CHAT, 7L, file.getFileKey(), "photo.png", 2048L,
                ChatErrorCode.INVALID_CHAT_ATTACHMENT);

        assertThat(file.getThumbnailStatus()).isEqualTo(ThumbnailStatus.PENDING);
        verify(eventPublisher).publishEvent(any(ThumbnailRequestedEvent.class));
    }

    /** 채팅 첨부는 이미지 전용이 아니다. pdf 가 Lambda 를 깨우면 그대로 낭비다. */
    @Test
    void 비이미지는_대상이_아니다() {
        UploadedFile file = stubConfirmable("application/pdf", AttachmentUsage.CHAT, "doc.pdf");

        service(true).confirmNew(AttachmentUsage.CHAT, 7L, file.getFileKey(), "doc.pdf", 2048L,
                ChatErrorCode.INVALID_CHAT_ATTACHMENT);

        assertThat(file.getThumbnailStatus()).isEqualTo(ThumbnailStatus.NONE);
        verify(eventPublisher, never()).publishEvent(any(ThumbnailRequestedEvent.class));
    }

    @Test
    void 이번_범위는_채팅뿐이라_POST는_대상이_아니다() {
        UploadedFile file = stubConfirmable("image/png", AttachmentUsage.POST, "photo.png");

        service(true).confirmNew(AttachmentUsage.POST, 7L, file.getFileKey(), "photo.png", 2048L,
                ChatErrorCode.INVALID_CHAT_ATTACHMENT);

        assertThat(file.getThumbnailStatus()).isEqualTo(ThumbnailStatus.NONE);
        verify(eventPublisher, never()).publishEvent(any(ThumbnailRequestedEvent.class));
    }

    /** 꺼진 환경에 큐가 쌓이면 나중에 켤 때 밀린 작업이 한꺼번에 터진다. */
    @Test
    void 비활성화면_PENDING을_찍지_않는다() {
        UploadedFile file = stubConfirmable("image/png", AttachmentUsage.CHAT, "photo.png");

        service(false).confirmNew(AttachmentUsage.CHAT, 7L, file.getFileKey(), "photo.png", 2048L,
                ChatErrorCode.INVALID_CHAT_ATTACHMENT);

        assertThat(file.getThumbnailStatus()).isEqualTo(ThumbnailStatus.NONE);
        verify(eventPublisher, never()).publishEvent(any(ThumbnailRequestedEvent.class));
    }

    private UploadedFileService service(boolean enabled) {
        return new UploadedFileService(repository, fileStorageService, eventPublisher,
                new ThumbnailProperties(enabled, "plog-thumbnail", 640));
    }

    private UploadedFile stubConfirmable(String contentType, AttachmentUsage usage,
                                         String fileName) {
        String fileKey = usage.keySegment() + "/users/7/abc/" + fileName;
        UploadedFile file = UploadedFile.issue(
                fileKey, 7L, usage, fileName, contentType, 2048L, NOW);

        given(repository.findByFileKey(fileKey)).willReturn(Optional.of(file));
        given(fileStorageService.headMatches(anyString(), anyLong(), anyString())).willReturn(true);
        given(repository.confirmIfPending(anyString(), any(), any(), any())).willReturn(1);
        return file;
    }
}
