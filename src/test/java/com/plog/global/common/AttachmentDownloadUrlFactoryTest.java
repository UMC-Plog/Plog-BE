package com.plog.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.global.config.ApiProperties;
import org.junit.jupiter.api.Test;

class AttachmentDownloadUrlFactoryTest {

    private final AttachmentDownloadUrlFactory factory =
            new AttachmentDownloadUrlFactory(new ApiProperties("https://api.umc-plog.site"));

    @Test
    void 게시글_첨부의_발급_엔드포인트_절대_URL을_만든다() {
        assertThat(factory.forPost(1L, 3L))
                .isEqualTo("https://api.umc-plog.site"
                        + "/api/projects/1/posts/attachments/3/download-url");
    }

    @Test
    void 업무카드_첨부의_발급_엔드포인트_절대_URL을_만든다() {
        assertThat(factory.forTask(1L, 3L))
                .isEqualTo("https://api.umc-plog.site"
                        + "/api/projects/1/tasks/attachments/3/download-url");
    }

    @Test
    void baseUrl_끝의_슬래시가_있어도_이중_슬래시가_생기지_않는다() {
        AttachmentDownloadUrlFactory trailing =
                new AttachmentDownloadUrlFactory(new ApiProperties("https://api.umc-plog.site/"));

        assertThat(trailing.forPost(1L, 3L))
                .isEqualTo("https://api.umc-plog.site"
                        + "/api/projects/1/posts/attachments/3/download-url");
    }
}
