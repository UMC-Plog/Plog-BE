package com.plog.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCompetencyAnchorCacheTest {

    @Mock EmbeddingClient embeddingClient;
    private TaskCompetencyAnchorCache cache;

    @BeforeEach
    void setUp() {
        cache = new TaskCompetencyAnchorCache(embeddingClient);
    }

    @Test
    void createsCentroidsForAllFourCompetencies() {
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResponse(List.of(1.0f, 2.0f), "test-model"));

        cache.warmUp();

        for (CompetencyCategory category : CompetencyCategory.values()) {
            assertThat(cache.centroidOf(category)).containsExactly(1.0f, 2.0f);
        }
        assertThat(cache.modelName()).isEqualTo("test-model");
    }

    @Test
    void rejectsMixedEmbeddingModelsDuringWarmUp() {
        when(embeddingClient.embed(anyString()))
                .thenReturn(new EmbeddingResponse(List.of(1.0f), "model-a"))
                .thenReturn(new EmbeddingResponse(List.of(1.0f), "model-b"));

        assertThatThrownBy(cache::warmUp).isInstanceOf(IllegalStateException.class);
    }
}
