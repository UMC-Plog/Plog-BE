package com.plog.infrastructure.s3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.global.util.TimeUtil;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

/**
 * 썸네일 생성 Lambda 를 호출한다. <b>트랜잭션 밖에서만</b> 불려야 한다
 * (ThumbnailRequestedEvent 참조).
 */
@Component
@Slf4j
public class ThumbnailInvoker {

    private final UploadedFileRepository uploadedFileRepository;
    private final LambdaAsyncClient lambdaAsyncClient;
    private final ObjectMapper objectMapper;
    private final ThumbnailProperties thumbnailProperties;
    private final String bucket;

    public ThumbnailInvoker(UploadedFileRepository uploadedFileRepository,
                            LambdaAsyncClient lambdaAsyncClient,
                            ObjectMapper objectMapper,
                            ThumbnailProperties thumbnailProperties,
                            @Value("${plog.s3.bucket:}") String bucket) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.lambdaAsyncClient = lambdaAsyncClient;
        this.objectMapper = objectMapper;
        this.thumbnailProperties = thumbnailProperties;
        this.bucket = bucket;
    }

    /**
     * PENDING 인 행만 처리한다. 즉시 Invoke 와 스케줄러 안전망이 같은 행을 동시에 집을 수
     * 있는데, 상태 확인이 없으면 READY 가 된 뒤에도 Lambda 가 또 돈다.
     */
    public void request(Long uploadedFileId) {
        Optional<UploadedFile> found = uploadedFileRepository.findById(uploadedFileId);
        if (found.isEmpty() || !found.get().isThumbnailPending()) {
            return;
        }
        request(found.get());
    }

    public void request(UploadedFile file) {
        String targetKey = ThumbnailKeys.of(file.getFileKey());

        // 발사 '전에' 기록한다. 뒤집으면 Lambda 가 아주 빨리 끝났을 때 at 이 아직 null 인
        // 창에서 스케줄러가 같은 파일을 중복 Invoke 한다.
        //
        // 이 UPDATE 는 동시에 기록이자 '선점'이다. WHERE 의 thumbnailAt IS NULL 이 락 역할을
        // 해서, 즉시 Invoke 와 스케줄러 안전망이 같은 행을 동시에 집어도 한쪽만 1을 받는다.
        // 진 쪽이 그냥 발사하면 Lambda 가 두 번 돌고 같은 객체를 두 번 쓴다.
        int claimed = uploadedFileRepository.markThumbnailRequested(
                file.getId(), targetKey, TimeUtil.now(), ThumbnailStatus.PENDING);
        if (claimed == 0) {
            return;
        }

        lambdaAsyncClient.invoke(InvokeRequest.builder()
                        .functionName(thumbnailProperties.functionName())
                        // EVENT 는 Lambda 가 큐에 넣고 202 를 즉시 준다. RequestResponse 로
                        // 바꾸면 채팅 전송이 Lambda 실행 시간만큼 느려진다.
                        .invocationType(InvocationType.EVENT)
                        .payload(SdkBytes.fromUtf8String(payload(file.getFileKey(), targetKey)))
                        .build())
                .whenComplete((response, error) -> {
                    // 실패해도 여기서 재시도하지 않는다. at 은 이미 찍혔으므로
                    // ThumbnailScheduler.confirmReady() 가 "객체 없음"으로 판정해
                    // attempts 를 올리고 재요청 상태로 되돌린다.
                    if (error != null) {
                        log.warn("thumbnail_invoke_failed fileId={} targetKey={}",
                                file.getId(), targetKey, error);
                    }
                });
    }

    private String payload(String sourceKey, String targetKey) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bucket", bucket,
                    "sourceKey", sourceKey,
                    "targetKey", targetKey,
                    "maxEdge", thumbnailProperties.maxEdge()));
        } catch (JsonProcessingException exception) {
            // Map.of 직렬화가 실패할 일은 없지만, 났다면 계약이 깨진 것이라 조용히 넘기지 않는다.
            throw new IllegalStateException("썸네일 payload 직렬화 실패", exception);
        }
    }
}
