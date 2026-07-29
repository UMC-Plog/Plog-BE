package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttachmentUsageTest {

    @Test
    void keySegment는_복수형이다() {
        assertThat(AttachmentUsage.POST.keySegment()).isEqualTo("posts");
        assertThat(AttachmentUsage.TASK.keySegment()).isEqualTo("tasks");
        assertThat(AttachmentUsage.CHAT.keySegment()).isEqualTo("chats");
    }

}
