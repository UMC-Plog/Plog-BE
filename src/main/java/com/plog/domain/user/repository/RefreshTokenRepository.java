package com.plog.domain.user.repository;

import com.plog.domain.user.entity.RefreshToken;
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
}
