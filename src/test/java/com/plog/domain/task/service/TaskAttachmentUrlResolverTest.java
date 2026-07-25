package com.plog.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskAttachmentUrlResolverTest {

    private static final String FILE_KEY = "temporary/task/users/1/abc/spec.docx";
    private static final String LINK_URL = "https://example.com/doc";
    private static final String PRESIGNED_URL = "https://s3.example.com/presigned";

    @Mock
    private FileStorageService fileStorageService;

    private TaskAttachmentUrlResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TaskAttachmentUrlResolver(fileStorageService);
    }

    private TaskAttachment fileAttachment() {
        return TaskAttachment.create(null, AttachmentType.FILE, "spec.docx", 2048L, FILE_KEY);
    }

    private TaskAttachment linkAttachment() {
        return TaskAttachment.create(null, AttachmentType.LINK, "설계 노션", null, LINK_URL);
    }

    @Test
    void resolvesAPresignedDownloadUrlForFileAttachments() {
        TaskAttachment attachment = fileAttachment();
        given(fileStorageService.createDownloadUrl(AttachmentUsage.TASK, FILE_KEY, "spec.docx"))
                .willReturn(PRESIGNED_URL);

        String result = resolver.resolve(attachment);

        assertThat(result).isEqualTo(PRESIGNED_URL);
        verify(fileStorageService).createDownloadUrl(AttachmentUsage.TASK, FILE_KEY, "spec.docx");
    }

    @Test
    void returnsTheOriginalUrlForLinkAttachmentsWithoutTouchingStorage() {
        TaskAttachment attachment = linkAttachment();

        String result = resolver.resolve(attachment);

        assertThat(result).isEqualTo(LINK_URL);
        verifyNoInteractions(fileStorageService);
    }
}