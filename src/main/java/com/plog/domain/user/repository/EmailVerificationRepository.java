package com.plog.domain.user.repository;

import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // findByEmail 은 목적을 무시해 다른 흐름의 행을 집어올 수 있으므로 제공하지 않는다.
    Optional<EmailVerification> findByEmailAndPurpose(String email, EmailVerificationPurpose purpose);

    /**
     * 시도횟수를 DB에서 원자적으로 1 증가시킨다. 엔티티로 읽고-더해-저장하면 동시에 들어온 오답 요청들이
     * 서로의 증가분을 덮어써(lost update) 병렬 요청만으로 최대 시도 횟수 제한을 우회할 수 있다.
     * <p>
     * REQUIRES_NEW 인 이유: 이 증가분은 "무차별 대입 차단"이 목적이라 뒤이어 던지는 예외에 휩쓸려
     * 롤백되면 안 된다. 호출자(EmailVerificationCodeService.verifyCode)가 트랜잭션을 열지 않는 지금도,
     * 미래에 누가 바깥을 트랜잭션으로 감싸도 증가는 독립적으로 커밋된다.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update EmailVerification v set v.attemptCount = v.attemptCount + 1 where v.id = :id")
    int increaseAttemptCount(@Param("id") Long id);

    /**
     * 탈퇴 처리용 — 해당 이메일의 인증 행을 목적 무관하게 전부 삭제한다.
     * 조회와 달리 여기서 purpose를 받지 않는 것이 의도다: 계정이 죽은 뒤 남겨둘 인증 행은 어떤 목적으로도 없다.
     */
    void deleteAllByEmail(String email);
}
