package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UploadedFileTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 7, 27, 10, 0);

    private UploadedFile pendingFile() {
        return UploadedFile.issue("chats/users/7/uuid/a.png", 7L, AttachmentUsage.CHAT,
                "a.png", "image/png", 100L, ISSUED);
    }

    @Test
    void 발급_직후에는_PENDING이고_taggedAt이_issuedAt과_같다() {
        UploadedFile file = pendingFile();

        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.PENDING);
        assertThat(file.getIssuedAt()).isEqualTo(ISSUED);
        // 클라이언트 PUT이 state=pending 태그를 싣기 때문에 재태깅 대상이 아니다.
        assertThat(file.getTaggedAt()).isEqualTo(ISSUED);
    }

    @Test
    void 해제하면_ORPHANED가_되고_taggedAt이_비워진다() {
        UploadedFile file = pendingFile();
        LocalDateTime releasedAt = ISSUED.plusHours(1);

        file.release(releasedAt);

        assertThat(file.getStatus()).isEqualTo(UploadedFileStatus.ORPHANED);
        assertThat(file.getReleasedAt()).isEqualTo(releasedAt);
        assertThat(file.getTaggedAt()).isNull();
    }

    @Test
    void markTagged는_taggedAt을_채운다() {
        UploadedFile file = pendingFile();
        file.release(ISSUED.plusHours(1));

        file.markTagged(ISSUED.plusHours(2));

        assertThat(file.getTaggedAt()).isEqualTo(ISSUED.plusHours(2));
    }

    @Test
    void tagValue는_소문자다() {
        assertThat(UploadedFileStatus.ORPHANED.tagValue()).isEqualTo("orphaned");
    }
}
