package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class CosineSimilarityTest {

    @Test
    void 완전히_같은_방향의_벡터는_유사도가_1이다() {
        List<Float> a = List.of(1.0f, 2.0f, 3.0f);
        List<Float> b = List.of(2.0f, 4.0f, 6.0f); // a와 스케일만 다른 같은 방향

        double similarity = CosineSimilarity.compute(a, b);

        assertThat(similarity).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void 직교하는_벡터는_유사도가_0이다() {
        List<Float> a = List.of(1.0f, 0.0f);
        List<Float> b = List.of(0.0f, 1.0f);

        double similarity = CosineSimilarity.compute(a, b);

        assertThat(similarity).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void 정반대_방향의_벡터는_유사도가_음수1이다() {
        List<Float> a = List.of(1.0f, 0.0f);
        List<Float> b = List.of(-1.0f, 0.0f);

        double similarity = CosineSimilarity.compute(a, b);

        assertThat(similarity).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void 차원이_다른_벡터는_비교할_수_없다() {
        List<Float> a = List.of(1.0f, 0.0f);
        List<Float> b = List.of(1.0f, 0.0f, 0.0f);

        assertThatThrownBy(() -> CosineSimilarity.compute(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 영벡터와는_비교할_수_없다() {
        List<Float> a = List.of(0.0f, 0.0f);
        List<Float> b = List.of(1.0f, 0.0f);

        assertThatThrownBy(() -> CosineSimilarity.compute(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_벡터는_비교할_수_없다() {
        assertThatThrownBy(() -> CosineSimilarity.compute(null, List.of(1.0f)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈_벡터는_비교할_수_없다() {
        assertThatThrownBy(() -> CosineSimilarity.compute(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void centroid는_원소별_평균이다() {
        List<List<Float>> vectors = List.of(
                List.of(1.0f, 1.0f),
                List.of(3.0f, 5.0f)
        );

        List<Float> centroid = CosineSimilarity.centroid(vectors);

        assertThat(centroid).containsExactly(2.0f, 3.0f);
    }

    @Test
    void centroid_벡터가_하나뿐이면_그대로_반환한다() {
        List<List<Float>> vectors = List.of(List.of(1.0f, 2.0f, 3.0f));

        List<Float> centroid = CosineSimilarity.centroid(vectors);

        assertThat(centroid).containsExactly(1.0f, 2.0f, 3.0f);
    }

    @Test
    void centroid_계산_대상이_비어있으면_예외() {
        assertThatThrownBy(() -> CosineSimilarity.centroid(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void centroid_차원이_다른_벡터가_섞이면_예외() {
        List<List<Float>> vectors = List.of(
                List.of(1.0f, 2.0f),
                List.of(1.0f, 2.0f, 3.0f)
        );

        assertThatThrownBy(() -> CosineSimilarity.centroid(vectors))
                .isInstanceOf(IllegalArgumentException.class);
    }
}