package com.plog.domain.user.repository;

import com.plog.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 탈퇴 후 유예기간이 지났고 아직 파기하지 않은 계정. 파기 배치의 대상. */
    List<User> findAllByDeletedAtBeforeAndAnonymizedAtIsNull(LocalDateTime threshold);
}
