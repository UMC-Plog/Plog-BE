package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

class ThumbnailInvokerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 29, 12, 0);

    private final UploadedFileRepository repository = mock(UploadedFileRepository.class);
    private final LambdaAsyncClient lambdaAsyncClient = mock(LambdaAsyncClient.class);

    private final ThumbnailInvoker invoker = new ThumbnailInvoker(
            repository, lambdaAsyncClient, new ObjectMapper(),
            new ThumbnailProperties(true, "plog-thumbnail", 640), "umc-plog-prod");

    /**
     * 동기 대기(RequestResponse)면 채팅 전송이 Lambda 실행만큼 느려진다.
     * EVENT 는 Lambda 가 큐에 넣고 202 를 즉시 준다.
     */
    @Test
    void EVENT_타입으로_호출한다() {
        stubPendingFile();

        invoker.request(11L);

        InvokeRequest request = capturedRequest();
        assertThat(request.invocationType()).isEqualTo(InvocationType.EVENT);
        assertThat(request.functionName()).isEqualTo("plog-thumbnail");
    }

    /** 키 규칙은 백엔드가 소유한다. Lambda 는 계산하지 않고 받은 값을 쓴다. */
    @Test
    void payload에_백엔드가_계산한_키를_싣는다() throws Exception {
        stubPendingFile();

        invoker.request(11L);

        var payload = new ObjectMapper().readTree(capturedRequest().payload().asUtf8String());
        assertThat(payload.get("bucket").asText()).isEqualTo("umc-plog-prod");
        assertThat(payload.get("sourceKey").asText()).isEqualTo("chats/users/7/abc/photo.png");
        assertThat(payload.get("targetKey").asText())
                .isEqualTo("thumbs/chats/users/7/abc/photo.png.webp");
        assertThat(payload.get("maxEdge").asInt()).isEqualTo(640);
    }

    /**
     * 발사 '전에' 기록해야 한다. 순서를 뒤집으면 Lambda 가 아주 빨리 끝났을 때
     * at 이 아직 null 인 창에서 스케줄러가 중복 Invoke 한다.
     */
    @Test
    void 요청시각과_키를_발사_전에_기록한다() {
        stubPendingFile();

        invoker.request(11L);

        verify(repository).markThumbnailRequested(
                eq(11L), eq("thumbs/chats/users/7/abc/photo.png.webp"),
                any(LocalDateTime.class), eq(ThumbnailStatus.PENDING));
    }

    /**
     * markThumbnailRequested 는 기록이자 선점이다. 즉시 Invoke 와 스케줄러 안전망이 같은
     * 행을 동시에 집으면 한쪽만 1을 받는데, 진 쪽이 그냥 발사하면 Lambda 가 두 번 돌고
     * 같은 객체를 두 번 쓴다.
     */
    @Test
    void 선점에_실패하면_발사하지_않는다() {
        stubPendingFile();
        given(repository.markThumbnailRequested(
                eq(11L), anyString(), any(LocalDateTime.class), eq(ThumbnailStatus.PENDING)))
                .willReturn(0);

        invoker.request(11L);

        verify(lambdaAsyncClient, never()).invoke(any(InvokeRequest.class));
    }

    /** 이미 READY 인 행에 다시 요청이 오면(중복 이벤트) 아무것도 하지 않는다. */
    @Test
    void PENDING이_아니면_호출하지_않는다() {
        UploadedFile file = UploadedFile.issue("chats/users/7/abc/photo.png", 7L,
                AttachmentUsage.CHAT, "photo.png", "image/png", 2048L, NOW);
        given(repository.findById(11L)).willReturn(Optional.of(file));

        invoker.request(11L);

        verify(lambdaAsyncClient, never()).invoke(any(InvokeRequest.class));
    }

    private void stubPendingFile() {
        UploadedFile file = UploadedFile.issue("chats/users/7/abc/photo.png", 7L,
                AttachmentUsage.CHAT, "photo.png", "image/png", 2048L, NOW);
        file.markThumbnailPending();
        // id 는 DB 가 채우는 값이라 issue() 로는 안 들어간다. markThumbnailRequested 가
        // 이 값을 쓰므로 반드시 세팅해야 한다.
        ReflectionTestUtils.setField(file, "id", 11L);
        given(repository.findById(11L)).willReturn(Optional.of(file));
        // 선점 성공이 기본. 실패 케이스는 해당 테스트가 따로 덮어쓴다.
        given(repository.markThumbnailRequested(
                eq(11L), anyString(), any(LocalDateTime.class), eq(ThumbnailStatus.PENDING)))
                .willReturn(1);
        given(lambdaAsyncClient.invoke(any(InvokeRequest.class)))
                .willReturn(CompletableFuture.completedFuture(InvokeResponse.builder().build()));
    }

    private InvokeRequest capturedRequest() {
        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
        verify(lambdaAsyncClient).invoke(captor.capture());
        return captor.getValue();
    }
}
