package com.plog.domain.notification.repository;

import com.plog.domain.notification.entity.FcmToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    Optional<FcmToken> findByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into fcm (user_id, token, created_at, updated_at)
            values (:userId, :token, now(), now())
            on conflict (token) do update
            set user_id = excluded.user_id,
                updated_at = now()
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId, @Param("token") String token);
}
