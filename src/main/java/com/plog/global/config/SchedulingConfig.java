package com.plog.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화. 사용처는 탈퇴 계정 개인정보 파기(WithdrawnUserPurgeScheduler),
 * S3 태깅·회수(UploadedFileTagScheduler), 썸네일 아웃박스(ThumbnailScheduler),
 * 리포트 자동 생성(ReportGenerationScheduler), Peer 평가 시작 알림이다.
 * <p>
 * 스레드풀 크기는 application.yaml 의 spring.task.scheduling.pool.size 에서 정한다.
 * 기본값 1로 두면 3초 주기 썸네일 확인이 200~400회 S3 호출을 하는 태깅 배치 뒤에
 * 줄을 서게 된다.
 * <p>
 * 인스턴스를 여러 대로 늘리면 같은 배치가 중복 실행된다. 썸네일 쪽은 UPDATE 의
 * thumbnailStatus 가드와 thumbnailAt IS NULL 선점으로 중복을 흡수하지만, 나머지 배치는
 * 그렇지 않으므로 그때는 분산 락이나 단일 실행 노드 지정이 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
