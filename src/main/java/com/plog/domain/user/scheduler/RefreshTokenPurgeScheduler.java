package com.plog.domain.user.scheduler;

import com.plog.domain.user.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료됐거나 회전 유예까지 끝난 리프레시 토큰을 지운다.
 * 회전이 DELETE 대신 usedAt 표시로 바뀌어서, 이 배치가 없으면 소모된 행이 계속 쌓인다.
 */
@Slf4j
@Component
public class RefreshTokenPurgeScheduler {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenPurgeScheduler(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    // 컨테이너 TZ는 Asia/Seoul 고정 — KST 기준 매일 04:00.
    // 탈퇴 파기(03:00), 소셜 가입 티켓 정리(03:30)와 시간을 벌려 둔다.
    @Scheduled(cron = "0 0 4 * * *")
    public void purge() {
        try {
            int purged = refreshTokenService.purgeExhausted();
            if (purged > 0) {
                log.info("만료·소모된 리프레시 토큰 삭제: {}건", purged);
            }
        } catch (RuntimeException e) {
            // 조용히 묻히면 표가 커지는 걸 아무도 모른다. 다음 주기에 다시 시도한다.
            log.error("만료 리프레시 토큰 삭제 실패", e);
        }
    }
}
