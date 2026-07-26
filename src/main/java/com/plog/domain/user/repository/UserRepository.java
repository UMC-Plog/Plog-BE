package com.plog.domain.user.repository;

import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 소셜 로그인 식별. 이메일이 아니라 provider 고유 ID로 찾는다 — 이메일은 바뀔 수 있다. */
    Optional<User> findByProviderTypeAndProviderId(ProviderType providerType, String providerId);

    boolean existsByNickname(String nickname);

    /** 탈퇴 후 유예기간이 지났고 아직 파기하지 않은 계정. 파기 배치의 대상. */
    List<User> findAllByDeletedAtBeforeAndAnonymizedAtIsNull(LocalDateTime threshold);
}
