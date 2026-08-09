package com.plog.domain.report.service;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카테고리별 anchor 문장({@link ActivityAnchors})을 앱 기동 시 1회 임베딩해서 centroid로 캐싱한다.
 * <p>
 * 활동 임베딩({@link ActivityEmbeddingService})과 anchor 임베딩은 반드시 같은 {@link EmbeddingClient}
 * 빈으로 계산해야 벡터 공간이 같아져서 코사인 유사도 비교가 의미를 갖는다 — 이 클래스가 별도
 * 하드코딩 벡터가 아니라 활동 임베딩과 동일한 빈을 주입받는 이유다.
 * <p>
 * 메모리 캐싱이라 앱 재시작 전까지만 유효하고, 재시작하면 다시 계산한다. 카테고리 6개 × 문장 5개
 * = API 호출 30번뿐이라 기동 시간에 미치는 영향은 무시할 수준 — 이 스케일에서 DB 저장은 과설계라
 * 채택하지 않았다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityAnchorCache {

    private final EmbeddingClient embeddingClient;

    private final Map<ActivityCategory, List<Float>> centroids = new ConcurrentHashMap<>();

    @PostConstruct
    void warmUp() {
        for (Map.Entry<ActivityCategory, List<String>> entry : ActivityAnchors.SENTENCES.entrySet()) {
            List<List<Float>> vectors = entry.getValue().stream()
                    .map(sentence -> embeddingClient.embed(sentence).vector())
                    .toList();
            centroids.put(entry.getKey(), CosineSimilarity.centroid(vectors));
        }
        log.info("activity_anchor_cache_warmed categories={}", centroids.keySet());
    }

    /**
     * 카테고리별 anchor centroid 벡터.
     *
     * @throws IllegalStateException 캐시가 아직 준비되지 않은 카테고리를 요청한 경우(기동 실패 등)
     */
    public List<Float> centroidOf(ActivityCategory category) {
        List<Float> vector = centroids.get(category);
        if (vector == null) {
            throw new IllegalStateException("anchor 캐시가 준비되지 않았습니다. category=" + category);
        }
        return vector;
    }

    /** 현재 캐시에 준비된 카테고리 목록. 분류 서비스가 유사도 비교 대상을 순회할 때 쓴다. */
    public Set<ActivityCategory> cachedCategories() {
        return Set.copyOf(centroids.keySet());
    }
}