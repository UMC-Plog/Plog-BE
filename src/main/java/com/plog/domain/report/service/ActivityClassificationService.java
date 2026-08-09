package com.plog.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 파이프라인 2단계(활동 유형 분류) 배치.
 * <p>
 * 3단계(임베딩)가 끝난 행만 대상으로 삼는다 — {@code embeddingModel}이 채워져 있어야
 * (실제 모델명이든 {@code EMBEDDING_NOT_APPLICABLE} sentinel이든) 이 배치가 집어간다. 이제 2단계가
 * 3단계에 의존하는 파이프라인 순서 변경이 여기 반영돼 있다.
 * <p>
 * content가 없는 유형(TASK_ATTACHMENT_ADD/TASK_STATUS_CHANGE)은 임베딩 자체가 없어(sentinel만
 * 찍힘) 벡터 비교 없이 {@link ActivityClassificationRules#classifyContentless}로 바로 분류한다.
 * 나머지는 저장된 벡터와 anchor centroid의 코사인 유사도를 비교해서, 임계값 미만이면
 * {@link ActivityClassificationRules#fallback}으로 넘어간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityClassificationService {

    /** 2단계 분류 대상 도메인 — 1단계 정제 대상과 동일(TASK/CHAT/POST). EVALUATION은 애초에
     *  noiseFiltered가 확정되지 않아(applyNoiseFilter가 이 도메인만 허용) 분류 대상이 될 수 없다. */
    private static final List<SourceDomain> CLASSIFIABLE_DOMAINS =
            List.of(SourceDomain.TASK, SourceDomain.CHAT, SourceDomain.POST);

    private static final Set<RawActivityType> CONTENTLESS_TYPES =
            Set.of(RawActivityType.TASK_ATTACHMENT_ADD, RawActivityType.TASK_STATUS_CHANGE);

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final Limit BATCH_LIMIT = Limit.of(500);

    private final ReportActivityLogRepository activityLogRepository;
    private final ActivityAnchorCache anchorCache;
    private final ObjectMapper objectMapper;

    @Transactional
    public int classifyBatch() {
        List<ReportActivityLog> targets = activityLogRepository
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        CLASSIFIABLE_DOMAINS, BATCH_LIMIT);
        if (targets.isEmpty()) {
            return 0;
        }

        for (ReportActivityLog activity : targets) {
            ActivityCategory category = classifyOne(activity);
            activity.classify(category);
        }

        log.info("activity_classification_batch_applied count={}", targets.size());
        return targets.size();
    }

    private ActivityCategory classifyOne(ReportActivityLog activity) {
        RawActivityType rawType = activity.getRawActivityType();
        if (CONTENTLESS_TYPES.contains(rawType)) {
            return ActivityClassificationRules.classifyContentless(rawType, activity.getMetadata(), objectMapper);
        }
        return classifyByEmbedding(activity, rawType);
    }

    private ActivityCategory classifyByEmbedding(ReportActivityLog activity, RawActivityType rawType) {
        List<Float> activityVector = parseVector(activity.getEmbedding(), activity.getId());
        if (activityVector == null) {
            // embeddingModel은 채워졌지만(N/A가 아닌 실제 모델) 벡터가 비어있는 비정상 상태 — 규칙 폴백.
            log.warn("activity_classification_missing_vector id={} embeddingModel={}",
                    activity.getId(), activity.getEmbeddingModel());
            return ActivityClassificationRules.fallback(rawType);
        }

        ActivityCategory best = null;
        double bestSimilarity = Double.NEGATIVE_INFINITY;
        for (ActivityCategory category : anchorCache.cachedCategories()) {
            double similarity = CosineSimilarity.compute(activityVector, anchorCache.centroidOf(category));
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                best = category;
            }
        }

        if (best == null || bestSimilarity < SIMILARITY_THRESHOLD) {
            return ActivityClassificationRules.fallback(rawType);
        }
        return best;
    }

    private List<Float> parseVector(String embeddingJson, Long activityId) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<List<Float>>() {
            });
        } catch (Exception e) {
            log.warn("activity_classification_vector_parse_failed id={}", activityId, e);
            return null;
        }
    }
}