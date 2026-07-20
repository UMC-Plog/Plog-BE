package com.plog.domain.notification.repository;

import com.plog.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationUserRepository extends JpaRepository<User, Long> {
}
