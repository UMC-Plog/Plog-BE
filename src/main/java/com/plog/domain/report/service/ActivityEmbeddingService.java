package com.plog.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.repository.projection.EmbeddingClaimProjection;
import com.plog.global.util.TimeUtil;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingRateLimitException;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리포트 파이프라인 3단계(임베딩 생성) 적용. 1단계에서 정제를 통과한(noiseFiltered=false) 활동을
 * 대상으로, 그 정제된 텍스트(Clean Activity)를 {@link EmbeddingClient}에 넘겨 벡터를 받아 저장한다.
 *
 * 외부 API 호출(embed())은 몇 초씩 걸릴 수 있어 트랜잭션 밖에서 한다 — 하나의 긴 트랜잭션 안에서
 * 최대 {@link #BATCH_SIZE}번 순차 호출하면 그동안 DB 커넥션과 커밋 안 된 변경을 계속 물고 있게
 * 된다. 대신 짧은 트랜잭션(선점 → 호출 밖 → 저장)으로 쪼갠다.
 *
 * 배치 1회 호출마다 {@link #leaseToken} UUID를 하나 발급해 선점한 행에 같이 찍는다. 이 토큰이
 * 두 가지를 가능하게 한다:
 *   429 즉시 해제 — 호출 한도에 걸리면 현재 행과 아직 처리 안 한 행의 리스를
 *       즉시 풀어서, 다음 배치 호출이 리스 만료(30분)를 기다리지 않고 바로 재시도하게 한다.
 *   도중 리스 연장 — {@link #LEASE_RENEWAL_INTERVAL}건마다 남은 행의 리스를
 *       갱신해서, 배치가 오래 걸려도(느린 응답 누적) 처리 중인 행이 리스 만료로 다른 실행자에게
 *       다시 선점되는 걸 막는다.
 *
 * 두 작업 모두 leaseToken이 일치하는 행만 건드린다 — 이미 리스가 만료돼 다른 실행자가 새로
 * 선점한 행을 실수로 같이 풀거나 연장하지 않기 위한 낙관적 동시성 체크다.
 *
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

    // 이 건수마다 남은 행의 리스를 갱신한다. 응답이 느려지는 구간이 몰려도 리스가 배치 전체
    // 처리 시간보다 먼저 끊기지 않도록 하기 위함.
    private static final int LEASE_RENEWAL_INTERVAL = 25;

    private final ReportActivityLogRepository activityLogRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public ActivityEmbeddingService(
            ReportActivityLogRepository activityLogRepository,
            ChatMessageRepository chatMessageRepository,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.activityLogRepository = activityLogRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    ActivityEmbeddingService(
            ReportActivityLogRepository activityLogRepository,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this(activityLogRepository, null, embeddingClient, objectMapper, transactionManager);
    }

    /**
     * 아직 임베딩되지 않은(embeddingModel이 null인) 정제 완료 활동 로그를 배치로 선점해 임베딩을 생성한다.
     * 임베딩할 텍스트가 없는 행은 {@link ReportActivityLog#markEmbeddingNotApplicable()}로 처리 완료
     * 표시만 한다. 개별 호출 실패(429 제외)는 해당 행만 건너뛰고(리스가 만료되면 다음 배치에서
     * 재시도된다) 배치 전체를 막지 않는다.
     * <p>
     * 호출 한도(429)에 걸리면 즉시 배치를 멈추고, 현재 행+아직 처리 안 한 행의 리스를 전부
     * 풀어준다 — 같은 시간 창 안에서는 계속 두드려봐야 API만 낭비하고, 리스를 그대로 두면
     * 다음 배치가 최대 30분을 기다려야 하기 때문이다.
     *
     * @return 이번 호출에서 실제로 벡터를 생성해 저장한 건수(처리 완료 표시만 한 건 제외)
     */
    public int embedBatch() {
        String leaseToken = UUID.randomUUID().toString();
        List<EmbeddingClaimProjection> claimed = claimBatch(leaseToken);
        if (claimed.isEmpty()) {
            return 0;
        }

        int embedded = 0;
        for (int i = 0; i < claimed.size(); i++) {
            if (i > 0 && i % LEASE_RENEWAL_INTERVAL == 0) {
                renewLease(remainingIds(claimed, i), leaseToken);
            }

            EmbeddingClaimProjection activity = claimed.get(i);
            EmbedResult result = processOne(activity.getId(), resolveContent(activity));
            if (result == EmbedResult.SUCCESS) {
                embedded++;
            } else if (result == EmbedResult.RATE_LIMITED) {
                releaseLease(remainingIds(claimed, i), leaseToken); // 현재 행 포함, 이후 전부
                log.warn("activity_embedding_rate_limited batch_stopped_early embedded={} total={}",
                        embedded, claimed.size());
                break;
            }
        }

        log.info("activity_embedding_batch_applied embedded={} total={}", embedded, claimed.size());
        return embedded;
    }

    /** CHAT 원문은 활동 로그에 복제하지 않고, 처리 순간에 원본 엔티티에서만 읽는다. */
    private String resolveContent(EmbeddingClaimProjection activity) {
        if (!SourceDomain.CHAT.name().equals(activity.getSourceDomain())) {
            return activity.getContent();
        }
        String sourceRefId = activity.getSourceRefId();
        if (sourceRefId == null || !sourceRefId.startsWith("chat:")) {
            return null;
        }
        try {
            Long chatMessageId = Long.valueOf(sourceRefId.substring("chat:".length()));
            return chatMessageRepository.findById(chatMessageId)
                    .map(message -> message.getMessage())
                    .orElse(null);
        } catch (NumberFormatException exception) {
            log.warn("chat_activity_invalid_source_ref sourceRefId={}", sourceRefId);
            return null;
        }
    }

    private List<Long> remainingIds(List<EmbeddingClaimProjection> claimed, int fromIndexInclusive) {
        return claimed.subList(fromIndexInclusive, claimed.size()).stream()
                .map(EmbeddingClaimProjection::getId)
                .toList();
    }

    /** 짧은 트랜잭션 — FOR UPDATE SKIP LOCKED로 선점하고 곧바로 리스+토큰을 찍은 뒤 커밋한다. */
    private List<EmbeddingClaimProjection> claimBatch(String leaseToken) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = TimeUtil.now();
            List<EmbeddingClaimProjection> claimed =
                    activityLogRepository.selectClaimableEmbeddingActivities(now, BATCH_SIZE);
            if (claimed.isEmpty()) {
                return List.of();
            }
            List<Long> ids = claimed.stream().map(EmbeddingClaimProjection::getId).toList();
            activityLogRepository.leaseForEmbedding(ids, now.plus(LEASE_DURATION), leaseToken);
            return claimed;
        });
    }

    /** 짧은 트랜잭션 — 아직 처리 안 한 행의 리스를 즉시 해제해 다음 배치가 바로 재시도하게 한다. */
    private void releaseLease(List<Long> ids, String leaseToken) {
        if (ids.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                activityLogRepository.releaseEmbeddingLease(ids, leaseToken));
    }

    /** 짧은 트랜잭션 — 아직 처리 안 한 행의 리스를 연장해 배치 도중 만료되지 않게 한다. */
    private void renewLease(List<Long> ids, String leaseToken) {
        if (ids.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                activityLogRepository.renewEmbeddingLease(ids, leaseToken, TimeUtil.now().plus(LEASE_DURATION)));
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
