package com.plog.domain.user.service;

import com.plog.domain.user.entity.SocialSignupTicket;
import com.plog.domain.user.repository.SocialSignupTicketRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.SocialSignupProperties;
import com.plog.global.util.HashUtil;
import com.plog.global.util.TimeUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 가입 티켓의 발급·소비·정리. 티켓 원문은 발급 순간에만 존재하고 DB에는 해시만 남는다.
 */
@Service
public class SocialSignupTicketService {

    private static final int TICKET_BYTE_LENGTH = 32; // 256-bit 엔트로피

    private final SocialSignupTicketRepository ticketRepository;
    private final SocialSignupProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public SocialSignupTicketService(SocialSignupTicketRepository ticketRepository,
                                     SocialSignupProperties properties) {
        this.ticketRepository = ticketRepository;
        this.properties = properties;
    }

    /** 티켓을 발급하고 원문을 반환한다. 같은 소셜 계정의 이전 티켓은 지운다. */
    @Transactional
    public String issue(SocialIdentity identity) {
        ticketRepository.deleteByProviderTypeAndProviderId(identity.provider(), identity.providerId());
        // 삭제를 INSERT 전에 DB에 반영한다 — 순서가 뒤집히면 이전 행이 그대로 남는다.
        ticketRepository.flush();

        String rawTicket = generateRawTicket();
        LocalDateTime now = TimeUtil.nowUtc();
        ticketRepository.save(SocialSignupTicket.issue(
                identity.provider(), identity.providerId(), identity.email(),
                HashUtil.sha256Hex(rawTicket), now.plus(properties.ticketTtl())));
        return rawTicket;
    }

    /**
     * 티켓을 검증하고 소비한 뒤 반환한다.
     * 호출자(SocialSignupService)의 트랜잭션에 참여하므로, 이후 단계가 실패하면 소비도 함께 롤백된다.
     * 의도한 동작이다 — 닉네임 중복 같은 이유로 실패한 사용자가 소셜 인증부터 다시 하지 않아도 된다.
     */
    @Transactional
    public SocialSignupTicket consumeOrThrow(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw new ApiException(AuthErrorCode.SOCIAL_TICKET_INVALID);
        }
        SocialSignupTicket ticket = ticketRepository.findByTicketHash(HashUtil.sha256Hex(rawTicket))
                .orElseThrow(() -> new ApiException(AuthErrorCode.SOCIAL_TICKET_INVALID));
        LocalDateTime now = TimeUtil.nowUtc();
        if (ticket.isExpired(now)) {
            throw new ApiException(AuthErrorCode.SOCIAL_TICKET_EXPIRED);
        }
        if (ticketRepository.consume(ticket.getId(), now) == 0) {
            throw new ApiException(AuthErrorCode.SOCIAL_TICKET_INVALID);
        }
        return ticket;
    }

    /** 만료됐거나 이미 소비된 티켓 삭제. 스케줄러가 호출한다. 반환값은 삭제한 행 수. */
    @Transactional
    public int purgeFinished() {
        return ticketRepository.deleteExpiredOrConsumed(TimeUtil.nowUtc());
    }

    private String generateRawTicket() {
        byte[] bytes = new byte[TICKET_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
