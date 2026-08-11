package com.plog.domain.task.service;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.service.CosineSimilarity;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 앱 기동 시 업무 제목 anchor를 임베딩해 역량별 centroid를 메모리에 캐시한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompetencyAnchorCache {

    private final EmbeddingClient embeddingClient;
    private final Map<CompetencyCategory, List<Float>> centroids = new EnumMap<>(CompetencyCategory.class);
    private volatile String anchorModel;

    @PostConstruct
    void warmUp() {
        String expectedModel = null;
        Map<CompetencyCategory, List<Float>> warmed = new EnumMap<>(CompetencyCategory.class);
        for (CompetencyCategory category : CompetencyCategory.values()) {
            List<List<Float>> vectors = new ArrayList<>();
            for (String sentence : TaskCompetencyAnchors.SENTENCES.get(category)) {
                EmbeddingResponse response = embeddingClient.embed(sentence);
                if (expectedModel == null) {
                    expectedModel = response.model();
                } else if (!expectedModel.equals(response.model())) {
                    throw new IllegalStateException("업무 제목 anchor 임베딩 모델이 섞였습니다. expected="
                            + expectedModel + ", actual=" + response.model());
                }
                vectors.add(response.vector());
            }
            warmed.put(category, CosineSimilarity.centroid(vectors));
        }
        centroids.clear();
        centroids.putAll(warmed);
        anchorModel = expectedModel;
        log.info("task_competency_anchor_cache_warmed categories={} model={}", centroids.keySet(), anchorModel);
    }

    public List<Float> centroidOf(CompetencyCategory category) {
        List<Float> centroid = centroids.get(category);
        if (centroid == null) {
            throw new IllegalStateException("업무 제목 역량 anchor 캐시가 준비되지 않았습니다. category=" + category);
        }
        return centroid;
    }

    public String modelName() {
        if (anchorModel == null) {
            throw new IllegalStateException("업무 제목 역량 anchor 캐시가 준비되지 않았습니다.");
        }
        return anchorModel;
    }
}
