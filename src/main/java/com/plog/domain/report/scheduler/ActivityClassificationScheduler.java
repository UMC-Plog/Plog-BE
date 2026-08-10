package com.plog.domain.report.scheduler;

import com.plog.domain.report.service.ActivityClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 리포트 파이프라인 2단계(활동 유형 분류)를 주기적으로 돌려 밀려있는 미분류 행을 비워나간다.
 * <p>
 * 실제 로직은 {@link ActivityClassificationService#classifyBatch()}에 있고 여기는 실행 주기만
 * 담당한다 — {@link ActivityRefinementScheduler}, {@link ActivityEmbeddingScheduler}와 같은 관례.
 * <p>
 * 3단계(임베딩) 선행 의존성: 분류 대상 조회 쿼리가 embedding_model이 채워진 행만 조회하므로,
 * 이 스케줄러가 임베딩 스케줄러보다 먼저 돌거나 더 자주 돌아도 잘못된(임베딩 안 된) 행을
 * 집어가는 일은 없다 — 그저 가져올 행이 없어 0건으로 끝난다. classifyBatch()는 외부 API 호출이
 * 없는 순수 벡터 연산이라(anchor는 기동 시 이미 캐싱됨) poll-delay는 정제 수준으로 짧게 잡는다.
 * <p>
 * 기본값은 꺼짐이다. anchor 임계값(0.5)이 실측 데이터로 아직 검증되지 않은 상태에서 자동으로
 * 켜지면 원인 파악 없이 대량의 classifiedType이 잘못 확정될 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plog.report.classification.enabled", havingValue = "true")
public class ActivityClassificationScheduler {

    private final ActivityClassificationService activityClassificationService;

    @Scheduled(fixedDelayString = "${plog.report.classification.poll-delay-ms:30000}")
    public void classify() {
        try {
            // 행 단위 실패는 여기까지 오지 않는다(분류는 순수 계산이라 개별 실패가 없다 —
            // 벡터 파싱 실패조차 규칙 폴백으로 흡수된다). 여기로 올라오는 건 대상 조회 실패처럼
            // 배치 전체가 못 도는 경우다.
            int count = activityClassificationService.classifyBatch();
            if (count > 0) {
                log.info("활동 로그 분류 스케줄 실행: count={}", count);
            }
        } catch (RuntimeException e) {
            log.error("활동 로그 분류 배치 실패", e);
        }
    }
}