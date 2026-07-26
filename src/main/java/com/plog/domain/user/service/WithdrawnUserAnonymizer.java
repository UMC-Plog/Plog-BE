package com.plog.domain.user.service;

import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 계정 "1건"의 개인정보 파기를 자기 트랜잭션 안에서 수행한다.
 * <p>
 * 배치(UserWithdrawalService.purgeExpired)와 굳이 별 빈으로 분리한 이유:
 * 같은 클래스의 메서드를 self-invocation으로 부르면 Spring 트랜잭션 프록시를 거치지 않아
 * REQUIRES_NEW가 그대로 무시된다(= 여전히 배치 전체가 한 트랜잭션). 빈 경계를 넘어 호출해야
 * 행마다 트랜잭션이 실제로 열리고, 한 행의 실패가 다른 행을 롤백시키지 못한다.
 * <p>
 * REQUIRES_NEW인 이유: 호출자가 트랜잭션 없이 부르는 지금은 REQUIRED로도 같지만,
 * 나중에 누군가 purgeExpired를 트랜잭션으로 감싸도 행 단위 격리가 유지되도록 못을 박는다.
 */
@Service
public class WithdrawnUserAnonymizer {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 익명화 값에 섞을 임의값 크기. 6바이트 = 16진수 12자(48비트) — 선점·추측이 사실상 불가능하다. */
    private static final int SUFFIX_BYTES = 6;
    private static final int SECRET_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public WithdrawnUserAnonymizer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 한 건을 익명화하고 즉시 flush한다.
     * flush를 여기서 하는 이유: 유니크 위반 같은 실패를 "이 트랜잭션 안에서" 터뜨려야
     * 호출자가 그 행만 실패로 기록하고 다음 행으로 넘어갈 수 있다.
     * 호출자는 트랜잭션 밖에서 조회한(=detached) 엔티티를 넘기므로 save로 이 트랜잭션에 병합한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anonymize(User target, LocalDateTime anonymizedAt) {
        target.anonymize(passwordEncoder.encode(randomSecret()), randomSuffix(), anonymizedAt);
        userRepository.saveAndFlush(target);
    }

    /** 로그인 불가 상태로 만들기 위한 임의 비밀값. 어디에도 남기지 않고 해시만 저장한다. */
    private String randomSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String randomSuffix() {
        byte[] bytes = new byte[SUFFIX_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
