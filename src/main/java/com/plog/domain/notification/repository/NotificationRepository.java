package com.plog.domain.notification.repository;

import com.plog.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // project는 목록 응답에 프로젝트명을 같이 내려주기 위해 fetch join한다(N+1 방지).
    // user는 호출자 본인이 이미 알고 있는 값이라 fetch join하지 않는다.
    @EntityGraph(attributePaths = {"project"})
    @Query("select n from Notification n where n.user.id = :userId order by n.id desc")
    Slice<Notification> findSliceByUserId(@Param("userId") Long userId, Pageable pageable);
}