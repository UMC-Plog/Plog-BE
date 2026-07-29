package com.plog.infrastructure.s3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class ThumbnailSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    private final UploadedFileRepository repository = mock(UploadedFileRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ThumbnailInvoker thumbnailInvoker = mock(ThumbnailInvoker.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final ThumbnailScheduler scheduler = new ThumbnailScheduler(
            repository, fileStorageService, thumbnailInvoker, eventPublisher);

    /** Invoke 가 유실됐거나 재요청으로 되돌려진 행을 집는 안전망. */
    @Test
    void 요청시각이_없는_행을_다시_요청한다() {
        UploadedFile file = pendingFile(11L);
        given(repository.findByThumbnailStatusAndThumbnailAtIsNull(
                eq(ThumbnailStatus.PENDING), any(Limit.class))).willReturn(List.of(file));

        scheduler.requestPending();

        verify(thumbnailInvoker).request(file);
    }

    @Test
    void 객체가_있으면_READY로_올리고_이벤트를_발행한다() {
        UploadedFile file = requestedFile(11L);
        stubConfirmTargets(file);
        given(fileStorageService.exists(anyString())).willReturn(true);
        given(repository.markThumbnailReady(
                eq(11L), eq(ThumbnailStatus.READY), eq(ThumbnailStatus.PENDING))).willReturn(1);

        scheduler.confirmReady();

        verify(repository).markThumbnailReady(
                11L, ThumbnailStatus.READY, ThumbnailStatus.PENDING);
        verify(eventPublisher).publishEvent(any(ThumbnailReadyEvent.class));
    }

    /** 다른 인스턴스가 먼저 올렸으면 가드가 0을 돌려준다. 같은 방에 push 가 두 번 가면 안 된다. */
    @Test
    void 이미_다른_인스턴스가_올렸으면_push하지_않는다() {
        UploadedFile file = requestedFile(11L);
        stubConfirmTargets(file);
        given(fileStorageService.exists(anyString())).willReturn(true);
        given(repository.markThumbnailReady(
                eq(11L), eq(ThumbnailStatus.READY), eq(ThumbnailStatus.PENDING))).willReturn(0);

        scheduler.confirmReady();

        verify(eventPublisher, never()).publishEvent(any(ThumbnailReadyEvent.class));
    }

    /**
     * 이 기능의 지연을 결정하는 규칙이다. 콜드스타트가 2~5초라 3초 틱의 첫 조회는 거의
     * 항상 빗나가는데, 여기서 thumbnailAt 을 비우면 그 행이 폴링 대상에서 영영 빠져
     * (thumbnailAt < threshold 에 NULL 은 안 걸린다) 30초 안전망이 정상 경로가 된다.
     * 그러면 모든 이미지가 Lambda 를 두 번 실행하고 지연이 2~6초에서 31초로 늘어난다.
     */
    @Test
    void 타임아웃_전에는_DB를_건드리지_않고_다음_틱에_다시_본다() {
        UploadedFile file = requestedFile(11L);
        setThumbnailAt(file, TimeUtil.nowUtc().minusSeconds(5));
        stubConfirmTargets(file);
        given(fileStorageService.exists(anyString())).willReturn(false);

        scheduler.confirmReady();

        verify(repository, never()).recordThumbnailAttempt(any(), anyInt(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(ThumbnailReadyEvent.class));
    }

    @Test
    void 타임아웃이_지나면_시도횟수를_올리고_재요청_상태로_되돌린다() {
        UploadedFile file = requestedFile(11L);
        setThumbnailAt(file, TimeUtil.nowUtc().minusSeconds(120));
        stubConfirmTargets(file);
        given(fileStorageService.exists(anyString())).willReturn(false);

        scheduler.confirmReady();

        verify(repository).recordThumbnailAttempt(
                eq(11L), anyInt(), eq(ThumbnailStatus.FAILED), eq(ThumbnailStatus.PENDING));
        verify(eventPublisher, never()).publishEvent(any(ThumbnailReadyEvent.class));
    }

    /**
     * 한 건의 S3 오류가 배치 전체를 막으면 그 행이 큐를 영구히 점유한다.
     * UploadedFileTagScheduler 와 같은 규약이다.
     */
    @Test
    void 한_건이_터져도_다음_건을_계속_처리한다() {
        UploadedFile broken = requestedFile(11L);
        UploadedFile healthy = requestedFile(12L);
        stubConfirmTargets(broken, healthy);
        given(fileStorageService.exists(broken.getThumbnailKey()))
                .willThrow(new RuntimeException("s3 down"));
        given(fileStorageService.exists(healthy.getThumbnailKey())).willReturn(true);
        given(repository.markThumbnailReady(
                eq(12L), eq(ThumbnailStatus.READY), eq(ThumbnailStatus.PENDING))).willReturn(1);

        scheduler.confirmReady();

        verify(repository).markThumbnailReady(
                12L, ThumbnailStatus.READY, ThumbnailStatus.PENDING);
    }

    private void stubConfirmTargets(UploadedFile... files) {
        given(repository.findByThumbnailStatusAndThumbnailAtBefore(
                eq(ThumbnailStatus.PENDING), any(LocalDateTime.class), any(Limit.class)))
                .willReturn(List.of(files));
    }

    private UploadedFile pendingFile(long id) {
        UploadedFile file = UploadedFile.issue("chats/users/7/abc" + id + "/photo.png", 7L,
                AttachmentUsage.CHAT, "photo.png", "image/png", 2048L, NOW);
        file.markThumbnailPending();
        ReflectionTestUtils.setField(file, "id", id);
        return file;
    }

    /**
     * markThumbnailRequested 는 JPQL UPDATE 라 메모리 인스턴스에 반영되지 않는다.
     * 스케줄러는 DB 에서 다시 읽은 행을 다루므로 여기서 직접 채워 둔다.
     * thumbnailAt 기본값은 '방금 요청함' — 타임아웃 판정 대상이 아니다.
     */
    private UploadedFile requestedFile(long id) {
        UploadedFile file = pendingFile(id);
        ReflectionTestUtils.setField(file, "thumbnailKey", ThumbnailKeys.of(file.getFileKey()));
        setThumbnailAt(file, TimeUtil.nowUtc());
        return file;
    }

    private void setThumbnailAt(UploadedFile file, LocalDateTime at) {
        ReflectionTestUtils.setField(file, "thumbnailAt", at);
    }
}
