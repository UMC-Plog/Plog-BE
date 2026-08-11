package com.plog.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTitleCompetencyClassifierTest {

    @Mock EmbeddingClient embeddingClient;
    @Mock TaskCompetencyAnchorCache anchorCache;
    private TaskTitleCompetencyClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new TaskTitleCompetencyClassifier(embeddingClient, anchorCache);
        lenient().when(embeddingClient.isRealProvider()).thenReturn(true);
        lenient().when(anchorCache.modelName()).thenReturn("test-model");
    }

    @Test
    void selectsTheClosestCompetency() {
        when(embeddingClient.embed("기술 스택 선정"))
                .thenReturn(new EmbeddingResponse(List.of(1.0f, 0.0f), "test-model"));
        when(anchorCache.centroidOf(CompetencyCategory.COLLABORATION)).thenReturn(List.of(0.0f, 1.0f));
        when(anchorCache.centroidOf(CompetencyCategory.LEADERSHIP)).thenReturn(List.of(1.0f, 0.0f));
        when(anchorCache.centroidOf(CompetencyCategory.COMMUNICATION)).thenReturn(List.of(-1.0f, 0.0f));
        when(anchorCache.centroidOf(CompetencyCategory.OUTPUT)).thenReturn(List.of(0.0f, -1.0f));

        var result = classifier.classify("기술 스택 선정");

        assertThat(result.competency()).isEqualTo(CompetencyCategory.LEADERSHIP);
        assertThat(result.confidence()).isEqualByComparingTo("1.0000");
    }

    @Test
    void breaksTiesByCompetencyEnumOrder() {
        when(embeddingClient.embed("동점 제목"))
                .thenReturn(new EmbeddingResponse(List.of(1.0f, 0.0f), "test-model"));
        for (CompetencyCategory category : CompetencyCategory.values()) {
            when(anchorCache.centroidOf(category)).thenReturn(List.of(1.0f, 0.0f));
        }

        assertThat(classifier.classify("동점 제목").competency())
                .isEqualTo(CompetencyCategory.values()[0]);
    }

    @Test
    void clampsNegativeSimilarityToZeroAndRoundsConsistently() {
        when(embeddingClient.embed("반대 방향"))
                .thenReturn(new EmbeddingResponse(List.of(1.0f, 0.0f), "test-model"));
        for (CompetencyCategory category : CompetencyCategory.values()) {
            when(anchorCache.centroidOf(category)).thenReturn(List.of(-1.0f, 0.0f));
        }

        var result = classifier.classify("반대 방향");

        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.confidence()).isBetween(new BigDecimal("0.0000"), new BigDecimal("1.0000"));
    }

    @Test
    void leavesAllFieldsNullForBlankTitle() {
        var result = classifier.classify("  ");

        assertThat(result.competency()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(result.classifierVersion()).isNull();
    }

    @Test
    void rejectsStubProviderInsteadOfSavingRandomClassification() {
        when(embeddingClient.isRealProvider()).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> classifier.classify("로그인 API 구현"))
                .isInstanceOf(IllegalStateException.class);
    }
}
