package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

class WithdrawnUserAnonymizerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 3, 0);

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private WithdrawnUserAnonymizer anonymizer;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        given(passwordEncoder.encode(anyString())).willAnswer(call -> "encoded:" + call.getArgument(0));
        anonymizer = new WithdrawnUserAnonymizer(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("한 건을 익명화하고 즉시 flush한다 (유니크 위반을 이 트랜잭션 안에서 표면화)")
    void anonymizesAndFlushesOneRow() {
        User target = withdrawnUser(41L);

        anonymizer.anonymize(target, NOW);

        assertThat(target.getEmail()).matches("withdrawn-41-[0-9a-f]{12}@deleted\\.plog");
        assertThat(target.getNickname()).matches("탈퇴한사용자-41-[0-9a-f]{12}");
        assertThat(target.getPassword()).startsWith("encoded:");
        assertThat(target.getAnonymizedAt()).isEqualTo(NOW);
        // 호출자는 트랜잭션 밖에서 조회한 detached 엔티티를 넘긴다 → save로 병합해야 변경이 저장된다.
        verify(userRepository).saveAndFlush(target);
    }

    @Test
    @DisplayName("호출마다 다른 임의값을 쓴다 (같은 값이 재사용되면 유니크 충돌이 다시 가능해진다)")
    void usesFreshRandomValuePerCall() {
        User first = withdrawnUser(42L);
        User second = withdrawnUser(43L);

        anonymizer.anonymize(first, NOW);
        anonymizer.anonymize(second, NOW);

        assertThat(suffixOf(first.getNickname())).isNotEqualTo(suffixOf(second.getNickname()));
        assertThat(first.getPassword()).isNotEqualTo(second.getPassword());
    }

    @Test
    @DisplayName("행 단위 격리가 실제로 걸리는 구조인지 지킨다: 별 빈 + public + REQUIRES_NEW")
    void isolationActuallyTakesEffect() throws Exception {
        // 같은 클래스에서 self-invocation으로 부르면 프록시를 거치지 않아 REQUIRES_NEW가 무시된다.
        // 그래서 (1) 별개의 스프링 빈이고 (2) 프록시가 가로챌 수 있는 public 메서드이며
        // (3) REQUIRES_NEW여야 한다 — 세 조건 중 하나만 깨져도 배치가 다시 한 트랜잭션으로 묶인다.
        assertThat(WithdrawnUserAnonymizer.class.isAnnotationPresent(Service.class)).isTrue();
        assertThat(UserWithdrawalService.class).isNotEqualTo(WithdrawnUserAnonymizer.class);

        Transactional annotation = WithdrawnUserAnonymizer.class
                .getMethod("anonymize", User.class, LocalDateTime.class)
                .getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);

        // 배치 쪽은 트랜잭션을 걸지 않아야 한다 — 걸어두면 전량 롤백 위험이 되살아난다.
        assertThat(UserWithdrawalService.class.getMethod("purgeExpired")
                .getAnnotation(Transactional.class)).isNull();
        assertThat(UserWithdrawalService.class.getAnnotation(Transactional.class)).isNull();
    }

    private User withdrawnUser(long id) {
        User user = User.createLocal("u" + id + "@plog.test", "encoded", "홍길동", "닉네임" + id,
                ProfilePreset.OTTER);
        // id는 IDENTITY 전략으로 DB가 채우므로 리플렉션으로 넣는다(anonymize가 id를 문자열에 쓴다).
        ReflectionTestUtils.setField(user, "id", id);
        user.withdraw(NOW.minusDays(7));
        return user;
    }

    private String suffixOf(String anonymizedNickname) {
        return anonymizedNickname.substring(anonymizedNickname.lastIndexOf('-') + 1);
    }
}
