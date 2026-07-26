package com.plog.domain.user.repository;

import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.SocialSignupTicket;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialSignupTicketRepository extends JpaRepository<SocialSignupTicket, Long> {

    Optional<SocialSignupTicket> findByTicketHash(String ticketHash);

    /**
     * 재발급 시 같은 소셜 계정의 이전 티켓을 지운다.
     * 로그인 버튼을 눌렀다 이탈하고 다시 누르는 것은 흔한 동작이라 행이 누적되면 안 된다.
     * 유니크 제약 대신 delete-then-insert 인 이유: 제약을 걸면 동시 요청에서 위반이 500으로 새어 나간다.
     */
    void deleteByProviderTypeAndProviderId(ProviderType providerType, String providerId);

    /**
     * 아직 소비되지 않은 티켓만 원자적으로 소비한다. 영향 행이 0이면 이미 쓰인 티켓이다.
     * 조회-검사-저장을 나누면 동시 요청 둘이 같은 티켓으로 함께 가입할 수 있다.
     */
    @Modifying
    @Query("update SocialSignupTicket t set t.consumedAt = :now where t.id = :id and t.consumedAt is null")
    int consume(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 더 쓰이지 않는 티켓 정리. 이 표는 실제 이메일을 담으므로 방치하면 개인정보가 무기한 쌓인다.
     * <p>
     * 소비된 행까지 지우는 이유: 소비 후에는 재사용 차단 외에 쓸모가 없는데, 행이 사라지면
     * 조회 자체가 실패해(SOCIAL_TICKET_INVALID) 차단 효과는 동일하다. 즉 남겨둘 이유가 없다.
     */
    @Modifying
    @Query("delete from SocialSignupTicket t where t.expiresAt < :threshold or t.consumedAt is not null")
    int deleteExpiredOrConsumed(@Param("threshold") LocalDateTime threshold);
}
