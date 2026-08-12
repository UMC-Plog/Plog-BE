package com.plog.domain.user.service;

import com.plog.domain.user.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재사용이 탐지된 계정의 세션을 끊는다. 별도 빈으로 분리한 이유는 트랜잭션 때문이다.
 * <p>
 * 탐지 지점은 곧바로 예외를 던져 재발급 트랜잭션을 롤백시킨다. 같은 트랜잭션에서 지우면
 * 그 롤백에 폐기까지 휩쓸려 아무 일도 일어나지 않고, 토큰을 쥔 쪽은 계속 회전할 수 있다.
 * REQUIRES_NEW 는 프록시를 거쳐야 적용되므로 자기 호출(self-invocation)이 되지 않게
 * 호출부(RefreshTokenService)와 다른 빈에 둔다.
 */
@Service
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }
}
