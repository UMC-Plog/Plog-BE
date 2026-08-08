package com.plog.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.repository.projection.EmbeddingClaimProjection;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingRateLimitException;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 파이프라인 3단계(임베딩 생성) 적용. 1단계에서 정제를 통과한(noiseFiltered=false) 활동을
 * 대상으로, 그 정제된 텍스트(Clean Activity)를 {@link EmbeddingClient}에 넘겨 벡터를 받아 저장한다.
 * <p>
 * 외부 API 호출(embed())은 몇 초씩 걸릴 수 있어 트랜잭션 밖에서 한다 — 하나의 긴 트랜잭션 안에서
 * 최대 {@link #BATCH_SIZE}번 순차 호출하면 그동안 DB 커넥션과 커밋 안 된 변경을 계속 물고 있게
 * 된다. 대신 세 단계로 쪼갠다:
 * <ol>
 *   <li>{@link #claimBatch()} — 짧은 트랜잭션. FOR UPDATE SKIP LOCKED로 처리할 행을
 *       원자적으로 선점(lease)한다. 동시에 여러 인스턴스가 이 메서드를 호출해도 같은 행을
 *       중복으로 집어가지 않는다.</li>
 *   <li>{@link #processOne} 안의 embed() 호출 — 트랜잭션 없음.</li>
 *   <li>결과 저장 — 짧은 트랜잭션. 성공/처리불필요 시 리스가 자동으로 해제된다.</li>
 * </ol>
 * 처리 중 앱이 죽어도 리스가 만료되면({@link #LEASE_DURATION}) 다음 배치 호출이 자동으로
 * 다시 집어간다 — 복구를 위한 별도 로직이 필요 없다.
 * <p>
 * cleanContent 자체는 저장하지 않으므로({@link ActivityContentRefiner}가 순수 함수) 매 배치마다
 * 원문에서 다시 계산한다 — 비용이 크지 않고, content 원문을 절대 수정하지 않는다는 엔티티 설계
 * 원칙과도 맞는다.
 */
@Slf4j
@Service
public class ActivityEmbeddingService {

    // 임베딩 호출은 정제(순수 계산)와 달리 네트워크 I/O가 섞여 있어 정제 배치(500)보다 작게 잡는다.
    private static final int BATCH_SIZE = 200;

    // 리스 유효기간. 개별 호출 최악의 경우(재시도 포함 커넥트+리드 타임아웃)를 다 합쳐도 넉넉히
    // 여유 있게 잡는다 — EvaluationActivityLogRecoveryScheduler의 processing-timeout(30m)과 같은 관례.
    private static final Duration LEASE_DURATION = Duration.ofMinutes(30);

    private final ReportActivityLogRepository activityLogRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ActivityEmbeddingService(
            ReportActivityLogRepository activityLogRepository,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.activityLogRepository = activityLogRepository;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 아직 임베딩되지 않은(embeddingModel이 null인) 정제 완료 활동 로그를 배치로 선점해 임베딩을 생성한다.
     * 임베딩할 텍스트가 없는 행은 {@link ReportActivityLog#markEmbeddingNotApplicable()}로 처리 완료
     * 표시만 한다. 개별 호출 실패는 해당 행만 건너뛰고(리스가 만료되면 다음 배치에서 재시도된다)
     * 배치 전체를 막지 않는다.
     * <p>
     * 단, 호출 한도(429)에 걸리면 즉시 배치를 멈춘다 — 같은 시간 창 안에서는 나머지도 다 똑같이
     * 막히니, 계속 두드려봐야 API만 낭비하고 로그만 늘어난다.
     *
     * @return 이번 호출에서 실제로 벡터를 생성해 저장한 건수(처리 완료 표시만 한 건 제외)
     */
    public int embedBatch() {
        List<EmbeddingClaimProjection> claimed = claimBatch();
        if (claimed.isEmpty()) {
            return 0;
        }

        int embedded = 0;
        for (EmbeddingClaimProjection activity : claimed) {
            EmbedResult result = processOne(activity.getId(), activity.getContent());
            if (result == EmbedResult.SUCCESS) {
                embedded++;
            } else if (result == EmbedResult.RATE_LIMITED) {
                log.warn("activity_embedding_rate_limited batch_stopped_early embedded={} total={}",
                        embedded, claimed.size());
                break;
            }
        }

        log.info("activity_embedding_batch_applied embedded={} total={}", embedded, claimed.size());
        return embedded;
    }

    /** 짧은 트랜잭션 — FOR UPDATE SKIP LOCKED로 선점하고 곧바로 리스를 찍은 뒤 커밋한다. */
    private List<EmbeddingClaimProjection> claimBatch() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<EmbeddingClaimProjection> claimed =
                    activityLogRepository.selectClaimableEmbeddingActivities(now, BATCH_SIZE);
            if (claimed.isEmpty()) {
                return List.of();
            }
            List<Long> ids = claimed.stream().map(EmbeddingClaimProjection::getId).toList();
            activityLogRepository.leaseForEmbedding(ids, now.plus(LEASE_DURATION));
            return claimed;
        });
    }

    private enum EmbedResult {
        SUCCESS, NOT_APPLICABLE, FAILED, RATE_LIMITED
    }

    /** 정제 재계산과 embed() 호출은 트랜잭션 밖에서 한다 — 결과 저장만 짧은 트랜잭션으로 감싼다. */
    private EmbedResult processOne(Long activityId, String rawContent) {
        RefinedContent refined = ActivityContentRefiner.refine(rawContent);
        if (!refined.hasCleanContent()) {
            transactionTemplate.executeWithoutResult(status -> markNotApplicable(activityId));
            return EmbedResult.NOT_APPLICABLE;
        }

        try {
            EmbeddingResponse response = embeddingClient.embed(refined.cleanContent());
            String vectorJson = writeVectorAsJson(response.vector());
            transactionTemplate.executeWithoutResult(status ->
                    saveEmbedding(activityId, response.model(), vectorJson));
            return EmbedResult.SUCCESS;
        } catch (EmbeddingRateLimitException e) {
            return EmbedResult.RATE_LIMITED;
        } catch (RuntimeException e) {
            log.warn("activity_embedding_failed activityId={}", activityId, e);
            return EmbedResult.FAILED;
        }
    }

    private void markNotApplicable(Long activityId) {
        activityLogRepository.findById(activityId).ifPresent(ReportActivityLog::markEmbeddingNotApplicable);
    }

    private void saveEmbedding(Long activityId, String model, String vectorJson) {
        activityLogRepository.findById(activityId)
                .ifPresent(activity -> activity.applyEmbedding(model, vectorJson));
    }

    private String writeVectorAsJson(List<Float> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            // ObjectMapper 직렬화 실패는 사실상 도달 불가(List<Float> 직렬화는 항상 성공)지만,
            // 방어적으로 감싸서 embed() 실패와 동일하게 processOne()의 catch에서 흡수되게 한다.
            throw new IllegalStateException("임베딩 벡터 직렬화에 실패했습니다.", e);
        }
    }
}