package com.plog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화. 현재 유일한 사용처는 탈퇴 계정 개인정보 파기(WithdrawnUserPurgeScheduler)다.
 * 인스턴스를 여러 대로 늘리면 같은 배치가 중복 실행되므로, 그때는 분산 락이나 단일 실행 노드 지정이 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
