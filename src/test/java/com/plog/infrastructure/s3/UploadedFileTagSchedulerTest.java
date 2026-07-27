package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class UploadedFileTagSchedulerTest {

    @Mock private UploadedFileRepository repository;
    @Mock private FileStorageService fileStorageService;
    @InjectMocks private UploadedFileTagScheduler scheduler;

    private UploadedFile orphanedFile() {
        UploadedFile file = UploadedFile.issue("chats/users/7/uuid/a.png", 7L,
                AttachmentUsage.CHAT, "a.png", "image/png", 100L,
                LocalDateTime.of(2026, 7, 27, 10, 0));
        file.release(LocalDateTime.of(2026, 7, 27, 11, 0));
        return file;
    }

    @Test
    void 태깅에_성공하면_taggedAt을_기록한다() {
        UploadedFile file = orphanedFile();
        given(repository.findByTaggedAtIsNull(any(Limit.class))).willReturn(List.of(file));
        given(fileStorageService.applyState(anyString(), any(), anyLong())).willReturn(true);

        scheduler.retryTagging();

        verify(repository).markTagged(eq(file.getId()), any());
    }

    @Test
    void 객체가_없어도_성공으로_처리해_무한재시도를_막는다() {
        UploadedFile file = orphanedFile();
        given(repository.findByTaggedAtIsNull(any(Limit.class))).willReturn(List.of(file));
        given(fileStorageService.applyState(anyString(), any(), anyLong())).willReturn(false);

        scheduler.retryTagging();

        // NoSuchKey = 정리할 객체 없음 = 성공. 기록하지 않으면 매 틱 재시도한다.
        verify(repository).markTagged(eq(file.getId()), any());
    }

    @Test
    void 방치된_PENDING_회수는_조건부_UPDATE로_한다() {
        given(repository.releaseAbandonedPending(any(), any(), any(), any())).willReturn(3);

        scheduler.reclaimAbandonedPending();

        // select 후 dirty checking 으로 바꾸면 그 사이 확정된 행까지 ORPHANED 로
        // 덮어써서 사용 중인 객체가 만료 삭제된다(#117 재발).
        verify(repository).releaseAbandonedPending(any(), any(),
                eq(UploadedFileStatus.ORPHANED), eq(UploadedFileStatus.PENDING));
        verify(repository, never()).findByStatusAndIssuedAtBefore(any(), any(), any(Limit.class));
    }

    @Test
    void 한_건의_S3_오류가_배치_전체를_막지_않는다() {
        UploadedFile poison = orphanedFile();
        UploadedFile healthy = orphanedFile();
        given(repository.findByTaggedAtIsNull(any(Limit.class)))
                .willReturn(List.of(poison, healthy));
        given(fileStorageService.applyState(anyString(), any(), anyLong()))
                .willThrow(new IllegalStateException("AccessDenied"))
                .willReturn(true);

        scheduler.retryTagging();

        // 실패한 건은 기록하지 않고(다음 틱 재시도) 뒤의 건은 그대로 진행한다.
        verify(repository, times(1)).markTagged(any(), any());
    }

    @Test
    void 유예가_지난_ORPHANED_행을_삭제한다() {
        UploadedFile file = orphanedFile();
        given(repository.findByStatusAndReleasedAtBefore(any(), any(), any(Limit.class)))
                .willReturn(List.of(file));

        scheduler.purgeReleasedRows();

        verify(repository).deleteAll(List.of(file));
    }
}
