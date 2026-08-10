package com.plog.domain.report.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 임베딩 벡터 간 코사인 유사도 계산 + anchor 문장 여러 개를 하나로 합치는 centroid 계산.
 * 순수 함수 — 외부 상태 없음, DB/네트워크 호출 없음.
 * <p>
 * 두 벡터를 비교하려면 반드시 같은 임베딩 모델로 생성됐어야 한다(차원이 같아도 모델이 다르면
 * 벡터 공간 자체가 달라 비교가 무의미하다) — 이 클래스는 차원 일치만 검증하고, 같은 모델인지는
 * 호출부(같은 {@code EmbeddingClient} 빈으로 activity/anchor 둘 다 생성)가 보장해야 한다.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * @throws IllegalArgumentException 두 벡터의 차원이 다르거나, 비었거나, 둘 중 하나가 영벡터인 경우
     */
    public static double compute(List<Float> a, List<Float> b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("벡터는 null일 수 없습니다.");
        }
        if (a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException("빈 벡터는 비교할 수 없습니다.");
        }
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "차원이 다른 벡터는 비교할 수 없습니다(모델이 다를 가능성). a=" + a.size() + ", b=" + b.size());
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }

        if (normA == 0.0 || normB == 0.0) {
            throw new IllegalArgumentException("영벡터와는 코사인 유사도를 계산할 수 없습니다.");
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 같은 차원의 벡터 여러 개를 원소별 평균낸 centroid 벡터를 만든다.
     * anchor 문장 5개를 각각 임베딩한 뒤 카테고리 하나를 대표하는 벡터 하나로 합칠 때 쓴다.
     *
     * @throws IllegalArgumentException 벡터 리스트가 비었거나, 섞인 벡터의 차원이 서로 다른 경우
     */
    public static List<Float> centroid(List<List<Float>> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("centroid를 계산할 벡터가 없습니다.");
        }
        int dimension = vectors.get(0).size();
        if (dimension == 0) {
            throw new IllegalArgumentException("빈 벡터로는 centroid를 계산할 수 없습니다.");
        }

        double[] sum = new double[dimension];
        for (List<Float> vector : vectors) {
            if (vector.size() != dimension) {
                throw new IllegalArgumentException(
                        "차원이 다른 벡터가 섞여 있습니다. 기대=" + dimension + ", 실제=" + vector.size());
            }
            for (int i = 0; i < dimension; i++) {
                sum[i] += vector.get(i);
            }
        }

        List<Float> result = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            result.add((float) (sum[i] / vectors.size()));
        }
        return result;
    }
}