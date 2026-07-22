package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttachmentUsageTest {

    @Test
    void lowercasesTheKeySegmentSoS3PrefixesStayConsistent() {
        assertThat(AttachmentUsage.CHAT.keySegment()).isEqualTo("chat");
        assertThat(AttachmentUsage.POST.keySegment()).isEqualTo("post");
        assertThat(AttachmentUsage.TASK.keySegment()).isEqualTo("task");
    }

    @Test
    void forcesDownloadEverywhereExceptChat() {
        assertThat(AttachmentUsage.POST.forcesDownload()).isTrue();
        assertThat(AttachmentUsage.TASK.forcesDownload()).isTrue();
        assertThat(AttachmentUsage.CHAT.forcesDownload()).isFalse();
    }
}
