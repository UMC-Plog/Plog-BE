package com.plog.domain.user.repository;

import com.plog.domain.user.entity.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByEmail(String email);
}
