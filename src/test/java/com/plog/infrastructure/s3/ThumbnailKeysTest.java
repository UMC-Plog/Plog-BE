package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThumbnailKeysTest {

    @Test
    void thumbs_접두사와_webp_접미사를_붙인다() {
        assertThat(ThumbnailKeys.of("chats/users/7/abc/photo.png"))
                .isEqualTo("thumbs/chats/users/7/abc/photo.png.webp");
    }

    /**
     * 확장자를 치환하지 않고 덧붙이는 이유. 치환하면 아래 둘이 같은 썸네일 키로
     * 충돌해 한쪽 썸네일이 다른 쪽을 덮어쓴다.
     */
    @Test
    void 대소문자만_다른_확장자가_충돌하지_않는다() {
        assertThat(ThumbnailKeys.of("chats/users/7/abc/photo.png"))
                .isNotEqualTo(ThumbnailKeys.of("chats/users/7/abc/photo.PNG"));
    }

    @Test
    void 확장자가_없어도_규칙이_깨지지_않는다() {
        assertThat(ThumbnailKeys.of("chats/users/7/abc/photo"))
                .isEqualTo("thumbs/chats/users/7/abc/photo.webp");
    }

    /** 원본 키에서 유도되므로 도메인 세그먼트가 그대로 보존된다 — IAM·Lifecycle 경계다. */
    @Test
    void 원본의_도메인_세그먼트를_보존한다() {
        assertThat(ThumbnailKeys.of("posts/users/1/xyz/a.jpg"))
                .startsWith("thumbs/posts/");
    }
}
