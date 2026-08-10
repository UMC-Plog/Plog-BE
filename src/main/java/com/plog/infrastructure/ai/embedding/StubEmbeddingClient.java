package com.plog.infrastructure.ai.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

/**
 * Ollama 서버 없이도 임베딩 파이프라인을 끝까지 돌려보기 위한 더미 클라이언트.
 * 로컬·CI 기본값이며, 설정이 비어 있을 때의 폴백이기도 하다.
 * <p>
 * 텍스트의 해시를 시드로 결정적(deterministic) 벡터를 만든다 — 매번 랜덤이면 같은 텍스트를
 * 두 번 넣어도 다른 벡터가 나와, 호출부의 저장/파싱 로직을 검증하기 어려워진다.
 */
@Slf4j
public class StubEmbeddingClient implements EmbeddingClient {

    private static final String MODEL_NAME = "stub";

    private final int dimension;

    public StubEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public EmbeddingResponse embed(String text) {
        log.warn("StubEmbeddingClient 사용 중 — 실제 임베딩이 아닙니다. "
                + "임베딩 벡터는 텍스트 해시 기반 더미입니다(plog.embedding.provider / EMBEDDING_BASE_URL 확인).");
        Random random = new Random(text == null ? 0 : text.hashCode());
        List<Float> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            vector.add(random.nextFloat() * 2 - 1); // [-1, 1)
        }
        return new EmbeddingResponse(vector, MODEL_NAME);
    }

    @Override
    public boolean isRealProvider() {
        return false;
    }
}