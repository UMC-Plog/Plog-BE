package com.plog.domain.user.repository;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // findByEmail 은 목적을 무시해 다른 흐름의 행을 집어올 수 있으므로 제공하지 않는다.
    Optional<EmailVerification> findByEmailAndPurpose(String email, EmailVerificationPurpose purpose);

    /**
     * 탈퇴 처리용 — 해당 이메일의 인증 행을 목적 무관하게 전부 삭제한다.
     * 조회와 달리 여기서 purpose를 받지 않는 것이 의도다: 계정이 죽은 뒤 남겨둘 인증 행은 어떤 목적으로도 없다.
     */
    void deleteAllByEmail(String email);
}
