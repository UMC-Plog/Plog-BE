package com.plog.domain.report.service;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
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
 * <b>같은 빈이어도 모델이 바뀔 수 있다는 점에 주의</b>: {@code EmbeddingClientConfig}가 Gemini→Ollama→
 * Stub 순으로 가용성에 따라 프로바이더를 고르기 때문에, 배포 중 프로바이더가 바뀌거나 Gemini
 * 모델 버전이 바뀌면 이 캐시가 새로 뜬 시점의 모델로 anchor를 계산해도 DB에 이미 저장된 활동
 * 벡터는 예전 모델로 만들어졌을 수 있다. 그래서 {@link #modelName()}으로 anchor 생성 시점의
 * 모델명을 노출해서, 호출부({@link ActivityClassificationService})가 활동의 embeddingModel과
 * 비교해 다르면 비교 자체를 건너뛸 수 있게 한다.
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
    private volatile String anchorModel;

    @PostConstruct
    void warmUp() {
        String expectedModel = null;
        for (Map.Entry<ActivityCategory, List<String>> entry : ActivityAnchors.SENTENCES.entrySet()) {
            List<List<Float>> vectors = new ArrayList<>();
            for (String sentence : entry.getValue()) {
                EmbeddingResponse response = embeddingClient.embed(sentence);
                if (expectedModel == null) {
                    expectedModel = response.model();
                } else if (!expectedModel.equals(response.model())) {
                    // 같은 EmbeddingClient 빈을 호출하는데도 모델이 섞여 나온다는 건 배치 도중
                    // 프로바이더/모델이 바뀌었다는 뜻 — anchor 전체가 같은 벡터 공간이라는 전제가
                    // 깨지므로 centroid를 계산해봤자 무의미하다. 기동 자체를 실패시켜 원인 파악을 강제한다.
                    throw new IllegalStateException(
                            "anchor 문장들이 서로 다른 임베딩 모델로 생성됐습니다. expected=" + expectedModel
                                    + ", actual=" + response.model() + ", sentence=" + sentence);
                }
                vectors.add(response.vector());
            }
            centroids.put(entry.getKey(), CosineSimilarity.centroid(vectors));
        }
        this.anchorModel = expectedModel;
        log.info("activity_anchor_cache_warmed categories={} model={}", centroids.keySet(), anchorModel);
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

    /**
     * anchor 벡터를 생성한 임베딩 모델명. 호출부가 활동의 {@code embeddingModel}과 비교해서
     * 다르면(예전 모델로 만든 벡터) 코사인 유사도 비교 자체를 건너뛰게 하는 용도다.
     *
     * @throws IllegalStateException 캐시가 아직 준비되지 않은 경우
     */
    public String modelName() {
        if (anchorModel == null) {
            throw new IllegalStateException("anchor 캐시가 아직 준비되지 않았습니다.");
        }
        return anchorModel;
    }
}