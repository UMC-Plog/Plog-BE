package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.plog.domain.report.entity.ActivityCategory;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAnchorCacheTest {

    @Mock private EmbeddingClient embeddingClient;

    private ActivityAnchorCache cache;

    @BeforeEach
    void setUp() {
        cache = new ActivityAnchorCache(embeddingClient);
    }

    /** 문장 해시 기반 결정적 더미 벡터 — 같은 문장이면 항상 같은 벡터, 다른 문장이면 다른 벡터. */
    private void stubDeterministicEmbeddings() {
        when(embeddingClient.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            float seed = Math.abs(text.hashCode() % 1000) + 1;
            return new EmbeddingResponse(List.of(seed, seed + 1, seed + 2), "test-model");
        });
    }

    @Test
    void 기동_전에는_모든_카테고리_조회가_예외다() {
        assertThatThrownBy(() -> cache.centroidOf(ActivityCategory.DECISION))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cache.cachedCategories()).isEmpty();
    }

    @Test
    void 워밍업하면_여섯_카테고리_모두_캐싱된다() {
        stubDeterministicEmbeddings();

        cache.warmUp();

        assertThat(cache.cachedCategories()).containsExactlyInAnyOrder(ActivityCategory.values());
        for (ActivityCategory category : ActivityCategory.values()) {
            assertThat(cache.centroidOf(category)).isNotEmpty();
        }
    }

    @Test
    void 카테고리별_centroid는_해당_카테고리_anchor_문장_임베딩의_평균이다() {
        // anchor 5개 전부 같은 고정 벡터를 돌려주도록 스텁하면 centroid도 같은 벡터여야 한다.
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResponse(List.of(1.0f, 2.0f, 3.0f), "test-model"));

        cache.warmUp();

        assertThat(cache.centroidOf(ActivityCategory.DECISION)).containsExactly(1.0f, 2.0f, 3.0f);
        assertThat(cache.centroidOf(ActivityCategory.SIMPLE_RESPONSE)).containsExactly(1.0f, 2.0f, 3.0f);
    }

    @Test
    void 서로_다른_카테고리는_서로_다른_centroid를_가진다() {
        stubDeterministicEmbeddings();

        cache.warmUp();

        List<Float> decision = cache.centroidOf(ActivityCategory.DECISION);
        List<Float> simpleResponse = cache.centroidOf(ActivityCategory.SIMPLE_RESPONSE);

        assertThat(decision).isNotEqualTo(simpleResponse);
    }

    @Test
    void 워밍업_전에_modelName을_조회하면_예외다() {
        assertThatThrownBy(() -> cache.modelName())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 워밍업하면_modelName이_anchor_임베딩_모델명을_돌려준다() {
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResponse(List.of(1.0f, 2.0f), "gemini-embedding-001"));

        cache.warmUp();

        assertThat(cache.modelName()).isEqualTo("gemini-embedding-001");
    }

    @Test
    void anchor_문장들이_서로_다른_모델로_임베딩되면_예외다() {
        // 같은 EmbeddingClient 빈을 호출하는데도 응답 모델이 섞여 나오는 비정상 상황을 흉내낸다
        // (예: 배치 도중 프로바이더가 전환됨). 첫 응답 이후 모델이 바뀌면 즉시 감지돼야 한다.
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResponse(List.of(1.0f), "model-a"))
                .thenReturn(new EmbeddingResponse(List.of(1.0f), "model-b"));

        assertThatThrownBy(() -> cache.warmUp())
                .isInstanceOf(IllegalStateException.class);
    }
}