package com.plog.domain.task.service;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.service.CosineSimilarity;
import com.plog.domain.task.entity.TaskCompetencyClassification;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 업무 제목이 요구하는 예상 역량을 분류한다. 실제 역량 발휘나 확률을 의미하지 않는다. */
@Service
@RequiredArgsConstructor
public class TaskTitleCompetencyClassifier {

    public static final String CLASSIFIER_VERSION = "task-title-anchor-v1";
    private static final int CONFIDENCE_SCALE = 4;

    private final EmbeddingClient embeddingClient;
    private final TaskCompetencyAnchorCache anchorCache;

    public TaskCompetencyClassification classify(String title) {
        if (title == null || title.isBlank()) {
            return TaskCompetencyClassification.unclassified();
        }
        if (!embeddingClient.isRealProvider()) {
            throw new IllegalStateException("실제 임베딩 프로바이더가 없어 업무 제목을 분류할 수 없습니다.");
        }

        var response = embeddingClient.embed(title);
        if (!anchorCache.modelName().equals(response.model())) {
            throw new IllegalStateException("업무 제목과 anchor의 임베딩 모델이 다릅니다. title="
                    + response.model() + ", anchor=" + anchorCache.modelName());
        }

        CompetencyCategory best = null;
        double bestSimilarity = Double.NEGATIVE_INFINITY;
        // enum 선언 순서로 순회하고 strict '>'를 사용해 동점 결과를 결정적으로 만든다.
        for (CompetencyCategory category : CompetencyCategory.values()) {
            double similarity = CosineSimilarity.compute(response.vector(), anchorCache.centroidOf(category));
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                best = category;
            }
        }
        double clamped = Math.max(0.0, Math.min(1.0, bestSimilarity));
        // confidence는 확률이 아니라 제목 임베딩과 선택된 anchor centroid의 코사인 유사도다.
        BigDecimal confidence = BigDecimal.valueOf(clamped).setScale(CONFIDENCE_SCALE, RoundingMode.HALF_UP);
        return new TaskCompetencyClassification(best, confidence, CLASSIFIER_VERSION);
    }
}
