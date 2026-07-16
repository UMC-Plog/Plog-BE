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
    @Query(value = "update fcm set user_id = :userId, updated_at = now() where fcm_id = :fcmId", nativeQuery = true)
    int updateOwnerAndTimestamp(@Param("fcmId") Long fcmId, @Param("userId") Long userId);
}
