package com.plog.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingRateLimitException;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 파이프라인 3단계(임베딩 생성) 적용. 1단계에서 정제를 통과한(noiseFiltered=false) 활동을
 * 대상으로, 그 정제된 텍스트(Clean Activity)를 {@link EmbeddingClient}에 넘겨 벡터를 받아 저장한다.
 * <p>
 * cleanContent 자체는 저장하지 않으므로({@link ActivityContentRefiner}가 순수 함수) 매 배치마다
 * 원문에서 다시 계산한다 — 비용이 크지 않고, content 원문을 절대 수정하지 않는다는 엔티티 설계
 * 원칙과도 맞는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityEmbeddingService {

    // 임베딩 호출은 정제(순수 계산)와 달리 네트워크 I/O가 섞여 있어 정제 배치(500)보다 작게 잡는다.
    private static final Limit BATCH_LIMIT = Limit.of(200);

    private final ReportActivityLogRepository activityLogRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    /**
     * 아직 임베딩되지 않은(embeddingModel이 null인) 정제 완료 활동 로그를 배치로 가져와 임베딩을 생성한다.
     * 임베딩할 텍스트가 없는 행은 {@link ReportActivityLog#markEmbeddingNotApplicable()}로 처리 완료
     * 표시만 한다. 개별 호출 실패는 해당 행만 건너뛰고(embeddingModel이 계속 null이라 다음 배치에서
     * 재시도된다) 배치 전체를 막지 않는다.
     * <p>
     * 단, 호출 한도(429)에 걸리면 즉시 배치를 멈춘다 — 같은 시간 창 안에서는 나머지도 다 똑같이
     * 막히니, 계속 두드려봐야 API만 낭비하고 로그만 늘어난다. 나머지는 embeddingModel이 null로
     * 남아 다음 스케줄 호출(시간이 지나 한도가 풀린 뒤)에서 자동으로 이어진다.
     *
     * @return 이번 호출에서 실제로 벡터를 생성해 저장한 건수(처리 완료 표시만 한 건 제외)
     */
    @Transactional
    public int embedBatch() {
        List<ReportActivityLog> targets =
                activityLogRepository.findByNoiseFilteredFalseAndEmbeddingModelIsNullOrderByOccurredAtAscIdAsc(
                        BATCH_LIMIT);
        if (targets.isEmpty()) {
            return 0;
        }

        int embedded = 0;
        for (ReportActivityLog activity : targets) {
            RefinedContent refined = ActivityContentRefiner.refine(activity.getContent());
            if (!refined.hasCleanContent()) {
                activity.markEmbeddingNotApplicable();
                continue;
            }
            EmbedResult result = tryEmbed(activity, refined.cleanContent());
            if (result == EmbedResult.SUCCESS) {
                embedded++;
            } else if (result == EmbedResult.RATE_LIMITED) {
                log.warn("activity_embedding_rate_limited batch_stopped_early embedded={} total={}",
                        embedded, targets.size());
                break;
            }
        }

        log.info("activity_embedding_batch_applied embedded={} total={}", embedded, targets.size());
        return embedded;
    }

    private enum EmbedResult {
        SUCCESS, FAILED, RATE_LIMITED
    }

    /** 한 건 실패가 배치 전체를 막지 않도록 예외를 여기서 흡수한다. */
    private EmbedResult tryEmbed(ReportActivityLog activity, String cleanContent) {
        try {
            EmbeddingResponse response = embeddingClient.embed(cleanContent);
            activity.applyEmbedding(response.model(), writeVectorAsJson(response.vector()));
            return EmbedResult.SUCCESS;
        } catch (EmbeddingRateLimitException e) {
            return EmbedResult.RATE_LIMITED;
        } catch (RuntimeException e) {
            log.warn("activity_embedding_failed activityId={}", activity.getId(), e);
            return EmbedResult.FAILED;
        }
    }

    private String writeVectorAsJson(List<Float> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            // ObjectMapper 직렬화 실패는 사실상 도달 불가(List<Float> 직렬화는 항상 성공)지만,
            // 방어적으로 감싸서 embed() 실패와 동일하게 tryEmbed()의 catch에서 흡수되게 한다.
            throw new IllegalStateException("임베딩 벡터 직렬화에 실패했습니다.", e);
        }
    }
}