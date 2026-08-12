package com.plog.domain.user.repository;

import com.plog.domain.user.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 해시에 해당하는 토큰을 단일 DELETE로 삭제하고 영향 행 수를 반환한다.
     * 동시 요청 중 하나만 1을 받고 나머지는 0을 받으므로, 반환값으로 토큰 "소모" 성공 여부를 판정할 수 있다.
     */
    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 아직 안 쓴 토큰만 소모 표시한다. 동시 요청 중 하나만 1을 받고 나머지는 0을 받으므로,
     * 반환값으로 "이번 요청이 첫 사용인지"를 판정한다. 지우지 않고 표시만 하는 이유는
     * 0을 받은 쪽이 재시도인지 탈취인지 usedAt 으로 구분해야 하기 때문이다.
     */
    @Modifying
    @Query("update RefreshToken t set t.usedAt = :now where t.tokenHash = :tokenHash and t.usedAt is null")
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /**
     * 소모 시각만 스칼라로 읽는다. 엔티티 조회로 읽으면 1차 캐시에 남은 값이 돌아와,
     * 방금 다른 트랜잭션이 커밋한 usedAt 을 못 보고 재시도를 탈취로 오판한다.
     */
    @Query("select t.usedAt from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<LocalDateTime> findUsedAtByTokenHash(@Param("tokenHash") String tokenHash);

    /** 비밀번호 재설정·탈퇴 시 해당 유저의 모든 세션을 끊는다. */
    void deleteAllByUserId(Long userId);

    /** 만료됐거나 유예까지 끝난 토큰 정리. 지우지 않고 표시만 하므로 이 배치가 없으면 표가 무한히 큰다. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now or t.usedAt < :usedBefore")
    int deleteExhausted(@Param("now") LocalDateTime now, @Param("usedBefore") LocalDateTime usedBefore);
}
