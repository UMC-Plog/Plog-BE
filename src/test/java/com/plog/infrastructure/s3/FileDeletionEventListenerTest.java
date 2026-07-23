package com.plog.infrastructure.s3;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.repository.PostAttachmentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileDeletionEventListenerTest {
    @Mock private FileStorageService fileStorageService;
    @Mock private PostAttachmentRepository postAttachmentRepository;
    @Mock private FileKeyLockService fileKeyLockService;

    private FileDeletionEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new FileDeletionEventListener(
                fileStorageService, postAttachmentRepository, fileKeyLockService);
    }

    @Test
    void keepsPostFileWhenReferenceStillExistsAfterCommit() {
        String fileKey = "temporary/post/users/1/shared/report.pdf";
        when(postAttachmentRepository.existsByAttachmentTypeAndFileUrl(AttachmentType.FILE, fileKey))
                .thenReturn(true);

        listener.deletePostFiles(new PostFileDeletionEvent(List.of(fileKey)));

        verify(fileStorageService, never()).delete(fileKey);
    }

    @Test
    void rechecksReferenceImmediatelyBeforeDeletingPostFile() {
        String fileKey = "temporary/post/users/1/shared/report.pdf";
        when(postAttachmentRepository.existsByAttachmentTypeAndFileUrl(AttachmentType.FILE, fileKey))
                .thenReturn(false);
        InOrder order = inOrder(fileKeyLockService, postAttachmentRepository, fileStorageService);

        listener.deletePostFiles(new PostFileDeletionEvent(List.of(fileKey)));

        order.verify(fileKeyLockService).lockAll(List.of(fileKey));
        order.verify(postAttachmentRepository)
                .existsByAttachmentTypeAndFileUrl(AttachmentType.FILE, fileKey);
        order.verify(fileStorageService).delete(fileKey);
    }
}
