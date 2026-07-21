package com.plog.domain.notification.repository;

import com.plog.domain.notification.entity.FcmToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    Optional<FcmToken> findByToken(String token);

    @EntityGraph(attributePaths = "user")
    List<FcmToken> findAllByUserIdIn(Collection<Long> userIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into fcm (user_id, token, created_at, updated_at)
            values (:userId, :token, now(), now())
            on conflict (token) do update
            set user_id = excluded.user_id,
                updated_at = now()
            """, nativeQuery = true)
    int upsert(@Param("userId") Long userId, @Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FcmToken f where f.token = :token and f.user.id = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from FcmToken f where f.token = :token")
    int deleteByToken(@Param("token") String token);
}
