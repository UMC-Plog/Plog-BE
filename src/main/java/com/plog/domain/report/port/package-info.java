/**
 * 리포트 5~6단계(LLM 생성 · 발행)가 앞단 1~4단계에 요구하는 <b>입력 계약</b>.
 * <p>
 * 리포트 생성 오케스트레이션은 다른 도메인의 서비스/리포지토리를 직접 부르지 않고 이 패키지의
 * 포트만 호출한다. 각 포트는 담당자가 다르고 완성 시점도 다르므로, 실제 구현이 없는 동안에는
 * {@code fake} 하위 패키지의 더미 구현이 {@code @ConditionalOnMissingBean} 으로 자리를 채운다.
 * 담당자가 실제 구현체를 빈으로 등록하는 순간 자동으로 교체된다 — 오케스트레이션 코드는 그대로다.
 *
 * <pre>
 *   InternalReportDataProvider  → 송민 (0~4단계 내부 도메인 집계)
 *   ExternalReportDataProvider  → 상완 (외부 연동 집계 · 신뢰도 등급)
 *   EvaluationSummaryProvider   → 서희 (Peer 보정 · 자기피드백 일치도)
 * </pre>
 *
 * 점수 조립(4-조립)은 포트를 두지 않는다 —
 * {@code com.plog.domain.report.service.ReportMemberScoreService} 가 이미 그 역할을 한다.
 * 오케스트레이션은 위 세 포트의 결과로 {@code MemberScoreInput} 을 만들어 그 서비스에 넘긴다.
 */
package com.plog.domain.report.port;
