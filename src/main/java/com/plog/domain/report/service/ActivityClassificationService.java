package com.plog.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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
 * <p>
 * <b>행 단위 실패 격리 + backoff/영구실패 전환</b>: 배치 전체가 하나의 트랜잭션이라, 한 행에서
 * 던진 예외를 그대로 두면 배치 전체가 롤백돼 정렬상 앞에 있는 잘못된 행 하나가 뒤의 정상 행까지
 * 전부 막아버린다. 벡터 자체의 결함(빈 배열/null 원소/영벡터)은 {@link #parseVector}가, anchor와
 * 차원이 달라 {@link CosineSimilarity#compute}가 던지는 예외는 {@link #classifyByEmbedding}이 각각
 * 잡아 규칙 폴백으로 돌린다. 그래도 예상 못한 예외가 나면 {@link #classifyBatch}의 행 단위
 * try-catch가 흡수하고 {@link #handleFailure}가 재시도 카운트를 올려 backoff 시각을 찍는다 —
 * classifiedType은 null로 남지만 조회 조건({@code classificationNextRetryAt}) 덕에 backoff가 끝나기
 * 전까지는 이 행이 다시 배치에 걸리지 않는다. 최대 재시도({@link #MAX_RETRY_COUNT})를 넘기면
 * {@code classificationFailed=true}로 확정해 이후 조회에서 영구히 제외한다(오래된 실패 행 하나가
 * Limit을 계속 소모해 정상 행을 굶기는 문제를 막기 위함).
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

    /** 이 횟수만큼 실패하면 더 이상 재시도하지 않고 classificationFailed=true로 확정한다. */
    private static final int MAX_RETRY_COUNT = 5;
    private static final Duration BASE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration MAX_BACKOFF = Duration.ofHours(2);

    private final ReportActivityLogRepository activityLogRepository;
    private final ActivityAnchorCache anchorCache;
    private final ObjectMapper objectMapper;

    @Transactional
    public int classifyBatch() {
        LocalDateTime now = LocalDateTime.now();
        List<ReportActivityLog> targets = activityLogRepository
                .findClassificationTargets(CLASSIFIABLE_DOMAINS, now, BATCH_LIMIT);
        if (targets.isEmpty()) {
            return 0;
        }

        int classifiedCount = 0;
        for (ReportActivityLog activity : targets) {
            try {
                ActivityCategory category = classifyOne(activity);
                activity.classify(category);
                classifiedCount++;
            } catch (RuntimeException e) {
                handleFailure(activity, e);
            }
        }

        log.info("activity_classification_batch_applied classified={} total={}", classifiedCount, targets.size());
        return classifiedCount;
    }

    /**
     * 이 행 하나의 실패가 배치 전체(트랜잭션)를 롤백시키면 안 된다. classify()를 호출하지 않아
     * classifiedType=null로 남기되, 재시도 카운트를 올리고 backoff 시각을 찍어서 다음 배치가
     * 곧바로 같은 행을 다시 집어가 반복 실패하지 않게 한다. 최대 재시도를 넘기면 영구 실패로
     * 확정해 조회 대상에서 아예 빠지게 한다.
     */
    private void handleFailure(ReportActivityLog activity, RuntimeException e) {
        int attemptNumber = activity.getClassificationRetryCount() + 1;
        if (attemptNumber > MAX_RETRY_COUNT) {
            activity.markClassificationFailed();
            log.error("activity_classification_row_permanently_failed id={} attempt={} — 더 이상 재시도하지 않습니다",
                    activity.getId(), attemptNumber, e);
            return;
        }

        Duration backoff = backoffFor(attemptNumber);
        LocalDateTime nextRetryAt = LocalDateTime.now().plus(backoff);
        activity.scheduleClassificationRetry(nextRetryAt);
        log.error("activity_classification_row_failed id={} attempt={} nextRetryAt={} — backoff 후 재처리 대상으로 남겨둡니다",
                activity.getId(), attemptNumber, nextRetryAt, e);
    }

    /** 지수 백오프, 상한(MAX_BACKOFF) 초과 방지. attemptNumber=1이면 BASE_BACKOFF 그대로. */
    private Duration backoffFor(int attemptNumber) {
        Duration backoff = BASE_BACKOFF.multipliedBy(1L << (attemptNumber - 1));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
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
            // embeddingModel은 채워졌지만(N/A가 아닌 실제 모델) 벡터가 없거나 결함이 있는
            // 비정상 상태 — 규칙 폴백.
            log.warn("activity_classification_invalid_vector id={} embeddingModel={}",
                    activity.getId(), activity.getEmbeddingModel());
            return ActivityClassificationRules.fallback(rawType);
        }

        String activityModel = activity.getEmbeddingModel();
        String anchorModel = anchorCache.modelName();
        if (!anchorModel.equals(activityModel)) {
            // 활동이 예전 임베딩 모델(또는 다른 프로바이더)로 만든 벡터를 갖고 있는 경우 — 차원이
            // 같아도 벡터 공간 자체가 달라 비교가 무의미하다. 재임베딩되기 전까지는 규칙 폴백으로
            // 처리한다(잘못된 카테고리로 확정되는 것보다 안전).
            log.warn("activity_classification_embedding_model_mismatch id={} activityModel={} anchorModel={}",
                    activity.getId(), activityModel, anchorModel);
            return ActivityClassificationRules.fallback(rawType);
        }

        try {
            Set<ActivityCategory> available = anchorCache.cachedCategories();
            ActivityCategory best = null;
            double bestSimilarity = Double.NEGATIVE_INFINITY;
            // ActivityCategory.values()는 enum 선언 순서로 항상 고정이다 — cachedCategories()가
            // 어떤 Set 구현으로 반환되든(HashSet/Set.of() 등은 iteration 순서가 JVM/런마다 달라질
            // 수 있음) 그 내부 순서에 기대지 않고 이 고정 순서로 순회해야, 유사도가 정확히 같은
            // 동점 상황에서도 매번 같은 카테고리가 선택된다. strict '>' 비교라 동점이면 먼저
            // 순회된(=enum에서 먼저 선언된) 카테고리가 그대로 유지된다.
            for (ActivityCategory category : ActivityCategory.values()) {
                if (!available.contains(category)) {
                    continue;
                }
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
        } catch (IllegalArgumentException e) {
            // 모델은 같다고 찍혀 있어도(데이터 이상 등으로) anchor와 차원이 다르면
            // CosineSimilarity.compute()가 여기서 던진다 — 이 행만 규칙 폴백으로 돌린다.
            log.warn("activity_classification_similarity_compute_failed id={}", activity.getId(), e);
            return ActivityClassificationRules.fallback(rawType);
        }
    }

    /**
     * 저장된 임베딩 JSON을 벡터로 역직렬화하고 기본적인 결함(빈 배열/null 원소/영벡터)을 미리
     * 걸러낸다. 문제가 있으면 예외 대신 null을 돌려줘서 호출부가 규칙 폴백으로 처리하게 한다 —
     * 코사인 유사도 계산 단계까지 넘기지 않아야 "예외로 배치가 죽는" 경로를 원천적으로 줄인다.
     */
    private List<Float> parseVector(String embeddingJson, Long activityId) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return null;
        }

        List<Float> vector;
        try {
            vector = objectMapper.readValue(embeddingJson, new TypeReference<List<Float>>() {
            });
        } catch (Exception e) {
            log.warn("activity_classification_vector_parse_failed id={}", activityId, e);
            return null;
        }

        if (vector.isEmpty()) {
            log.warn("activity_classification_empty_vector id={}", activityId);
            return null;
        }
        if (vector.stream().anyMatch(Objects::isNull)) {
            log.warn("activity_classification_vector_has_null_element id={}", activityId);
            return null;
        }
        if (vector.stream().allMatch(v -> v == 0.0f)) {
            log.warn("activity_classification_zero_vector id={}", activityId);
            return null;
        }

        return vector;
    }
}