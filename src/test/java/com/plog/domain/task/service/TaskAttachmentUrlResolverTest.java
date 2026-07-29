package com.plog.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.global.common.AttachmentDownloadUrlFactory;
import com.plog.global.config.ApiProperties;
import org.junit.jupiter.api.Test;

/**
 * 예전에는 여기서 presigned 를 직접 발급했다. 지금은 "발급 API 가 어디 있는지"만 만든다.
 * id 가 URL 에 들어가므로 영속화되지 않은 엔티티(id == null) 대신 mock 을 쓴다.
 */
class TaskAttachmentUrlResolverTest {

    private final TaskAttachmentUrlResolver resolver = new TaskAttachmentUrlResolver(
            new AttachmentDownloadUrlFactory(new ApiProperties("https://api.umc-plog.site")));

    @Test
    void FILE_첨부는_발급_엔드포인트_URL을_돌려준다() {
        TaskAttachment attachment = mock(TaskAttachment.class);
        given(attachment.getAttachmentType()).willReturn(AttachmentType.FILE);
        given(attachment.getId()).willReturn(3L);

        assertThat(resolver.resolveDownloadUrlApi(1L, attachment))
                .isEqualTo("https://api.umc-plog.site"
                        + "/api/projects/1/tasks/attachments/3/download-url");
    }

    @Test
    void LINK_첨부는_null을_돌려준다() {
        TaskAttachment attachment = mock(TaskAttachment.class);
        given(attachment.getAttachmentType()).willReturn(AttachmentType.LINK);

        assertThat(resolver.resolveDownloadUrlApi(1L, attachment)).isNull();
    }
}
