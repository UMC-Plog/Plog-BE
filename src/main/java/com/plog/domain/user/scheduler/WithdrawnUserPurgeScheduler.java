package com.plog.domain.user.scheduler;

import com.plog.domain.user.service.UserWithdrawalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 후 유예기간이 지난 계정의 개인정보를 파기한다.
 * 파기 시점에 이메일·닉네임 유니크 자리가 풀려 재가입이 가능해진다.
 */
@Slf4j
@Component
public class WithdrawnUserPurgeScheduler {

    private final UserWithdrawalService userWithdrawalService;

    public WithdrawnUserPurgeScheduler(UserWithdrawalService userWithdrawalService) {
        this.userWithdrawalService = userWithdrawalService;
    }

    // 컨테이너 TZ는 Asia/Seoul로 고정되어 있다(docker-compose.yml의 TZ) — 이 cron은 KST 기준 매일 03:00이다.
    // 트래픽이 적은 새벽 시간대를 골라 배치가 실 서비스 부하와 겹치지 않게 한다.
    @Scheduled(cron = "0 0 3 * * *")
    public void purge() {
        try {
            // purgeExpired는 행 단위로 격리되어 있어 개별 실패는 여기까지 오지 않는다(그쪽에서 ERROR 로깅).
            // 여기로 올라오는 건 대상 조회 실패처럼 배치 전체가 못 도는 경우다 — 조용히 묻히면 안 되므로 남긴다.
            int purged = userWithdrawalService.purgeExpired();
            if (purged > 0) {
                log.info("탈퇴 계정 개인정보 파기 완료: {}건", purged);
            }
        } catch (RuntimeException e) {
            log.error("탈퇴 계정 개인정보 파기 배치 실패", e);
        }
    }
}
