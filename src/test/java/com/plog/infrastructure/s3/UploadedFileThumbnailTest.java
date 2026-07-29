package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UploadedFileThumbnailTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    @Test
    void 새로_발급된_파일은_썸네일_대상이_아니다() {
        UploadedFile file = issued();

        assertThat(file.getThumbnailStatus()).isEqualTo(ThumbnailStatus.NONE);
        assertThat(file.isThumbnailPending()).isFalse();
        assertThat(file.isThumbnailReady()).isFalse();
    }

    /**
     * PENDING 으로 표시할 때 thumbnailAt 을 반드시 비운다. 남아 있으면 스케줄러의
     * requestPending() 쿼리(at IS NULL)에 안 걸려 안전망이 동작하지 않는다.
     */
    @Test
    void 대상으로_표시하면_PENDING이_되고_요청시각이_비워진다() {
        UploadedFile file = issued();

        file.markThumbnailPending();

        assertThat(file.getThumbnailStatus()).isEqualTo(ThumbnailStatus.PENDING);
        assertThat(file.isThumbnailPending()).isTrue();
        assertThat(file.getThumbnailAt()).isNull();
    }

    private UploadedFile issued() {
        return UploadedFile.issue("chats/users/7/abc/photo.png", 7L, AttachmentUsage.CHAT,
                "photo.png", "image/png", 2048L, NOW);
    }
}
