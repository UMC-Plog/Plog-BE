package com.plog.domain.report.port;

/**
 * 담당: 서희 (Peer 평가 Z-score 보정 · 자기 피드백 로그 일치도).
 * <p>
 * 구현체를 {@code @Component} 로 등록하면 {@code FakeEvaluationSummaryProvider} 를 밀어내고
 * 자동으로 사용된다.
 */
public interface EvaluationSummaryProvider {

    /**
     * 멤버 1명이 받은 Peer 평가 집계.
     * <p>
     * 계약: 받은 평가가 없으면 예외 대신 {@link PeerEvaluationSummary#none()} 을 돌려줄 것.
     * 팀이 평가를 마치지 않은 채 타임아웃으로 리포트가 발행되는 경로가 실제로 존재한다
     * ({@code ProjectStatusService} 의 endDay+7일 타임아웃).
     */
    PeerEvaluationSummary peer(Long projectId, Long projectMemberId);

    /**
     * 멤버 1명의 자기 피드백 일치도.
     * <p>
     * 계약: 자기 피드백은 프로젝트 완료의 필수 조건이 아니다(Peer 평가만 조건이다).
     * 미제출이면 예외 대신 {@link SelfFeedbackMatchSummary#notSubmitted()} 를 돌려줄 것.
     */
    SelfFeedbackMatchSummary self(Long projectId, Long projectMemberId);
}
