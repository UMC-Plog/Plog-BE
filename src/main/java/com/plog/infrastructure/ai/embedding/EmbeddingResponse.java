package com.plog.infrastructure.ai.embedding;

import java.util.List;

/**
 * 프로바이더 중립 임베딩 응답.
 *
 * @param vector 임베딩 벡터. 비어 있을 수 없다
 * @param model  실제로 응답한 모델명. 프로퍼티의 모델명과 다를 수 있어(별칭 등) 그대로 기록해 둔다 —
 *               나중에 모델이 바뀌었을 때 어떤 행이 이전 모델로 생성됐는지 추적할 수 있다
 */
public record EmbeddingResponse(List<Float> vector, String model) {
    public EmbeddingResponse {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("vector must not be empty");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
    }

    public int dimension() {

        return vector.size();
    }
}