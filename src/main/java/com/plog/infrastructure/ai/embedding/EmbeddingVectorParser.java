package com.plog.infrastructure.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link GeminiEmbeddingClient}와 {@link OllamaEmbeddingClient}가 공유하는 embedding 배열
 * 파싱/검증 로직.
 * <p>
 * {@code JsonNode.asDouble()}은 null/문자열/객체/배열 원소를 조용히 0.0으로 변환해버린다.
 * 검증 없이 그대로 쓰면, 프로바이더가 이상한 응답(예: 일부 원소가 null)을 줘도 "그럴듯한
 * 0이 섞인 벡터"로 둔갑해 저장되고, 이게 나중에 유사도 계산을 조용히 왜곡한다. 그래서
 * 원소 하나하나가 진짜 숫자이고 유한(finite)한지 여기서 명시적으로 검증한다.
 * <p>
 * double → float 변환 자체도 한 번 더 검증한다. double로는 유한해도(예: 1e40) float 범위
 * (~3.4e38)를 넘으면 캐스팅 결과가 Infinity가 된다 — double 단계 검증만으로는 못 잡는다.
 */
final class EmbeddingVectorParser {

    private EmbeddingVectorParser() {
    }

    static List<Float> parseFiniteVector(JsonNode arrayNode, String sourceDescription) {
        List<Float> vector = new ArrayList<>(arrayNode.size());
        for (JsonNode value : arrayNode) {
            if (!value.isNumber()) {
                throw new EmbeddingGenerationException(
                        sourceDescription + "의 embedding 배열에 숫자가 아닌 원소가 있습니다: " + value);
            }
            double raw = value.asDouble();
            if (Double.isNaN(raw) || Double.isInfinite(raw)) {
                throw new EmbeddingGenerationException(
                        sourceDescription + "의 embedding 배열에 유한하지 않은 값이 있습니다: " + raw);
            }
            float narrowed = (float) raw;
            if (!Float.isFinite(narrowed)) {
                // double로는 유한했지만 float 범위(약 ±3.4e38)를 벗어나 Infinity가 된 경우.
                throw new EmbeddingGenerationException(
                        sourceDescription + "의 embedding 값이 float 범위를 벗어납니다: " + raw);
            }
            vector.add(narrowed);
        }
        return vector;
    }
}