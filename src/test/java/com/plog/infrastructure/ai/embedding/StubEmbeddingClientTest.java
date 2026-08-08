package com.plog.infrastructure.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StubEmbeddingClientTest {

    private final StubEmbeddingClient client = new StubEmbeddingClient(16);

    @Test
    void 요청한_차원만큼_벡터를_생성한다() {
        EmbeddingResponse response = client.embed("아무 텍스트");

        assertThat(response.vector()).hasSize(16);
        assertThat(response.model()).isEqualTo("stub");
    }

    @Test
    void 같은_텍스트는_항상_같은_벡터를_돌려준다() {
        EmbeddingResponse first = client.embed("동일한 텍스트");
        EmbeddingResponse second = client.embed("동일한 텍스트");

        assertThat(first.vector()).isEqualTo(second.vector());
    }

    @Test
    void 다른_텍스트는_다른_벡터를_돌려준다() {
        EmbeddingResponse first = client.embed("텍스트 A");
        EmbeddingResponse second = client.embed("텍스트 B");

        assertThat(first.vector()).isNotEqualTo(second.vector());
    }

    @Test
    void 실제_프로바이더가_아니다() {
        assertThat(client.isRealProvider()).isFalse();
    }
}