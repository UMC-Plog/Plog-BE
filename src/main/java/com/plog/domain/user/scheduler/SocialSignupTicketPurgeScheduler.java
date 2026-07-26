package com.plog.domain.user.scheduler;

import com.plog.domain.user.service.SocialSignupTicketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료된 소셜 가입 티켓을 삭제한다.
 * 이 표는 실제 이메일을 담기 때문에 방치하면 "인증만 하고 가입은 안 한" 사람의 개인정보가 무기한 남는다
 * (email_verification 이 같은 이유로 뒤늦게 정리 경로를 추가해야 했다).
 */
@Slf4j
@Component
public class SocialSignupTicketPurgeScheduler {

    private final SocialSignupTicketService socialSignupTicketService;

    public SocialSignupTicketPurgeScheduler(SocialSignupTicketService socialSignupTicketService) {
        this.socialSignupTicketService = socialSignupTicketService;
    }

    // 컨테이너 TZ는 UTC 고정이다 — UTC 기준 매일 03:30.
    // 탈퇴 파기 배치(03:00)와 시간을 벌려 두 배치가 겹치지 않게 한다.
    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        try {
            int purged = socialSignupTicketService.purgeFinished();
            if (purged > 0) {
                log.info("만료·소비된 소셜 가입 티켓 삭제: {}건", purged);
            }
        } catch (RuntimeException e) {
            // 조용히 묻히면 개인정보가 남은 사실을 아무도 모른다. 배치는 다음 주기에 다시 시도한다.
            log.error("만료 소셜 가입 티켓 삭제 실패", e);
        }
    }
}
