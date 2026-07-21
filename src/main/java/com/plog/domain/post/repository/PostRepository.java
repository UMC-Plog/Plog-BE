package com.plog.domain.post.repository;

import com.plog.domain.post.entity.Post;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findByIdAndProjectMemberProjectId(Long id, Long projectId);

    Optional<Post> findFirstByProjectMemberProjectIdAndIsNoticeTrue(Long projectId);

    List<Post> findAllByProjectMemberProjectIdAndIsNoticeTrue(Long projectId);

    @Query("""
            select p from Post p
            where p.projectMember.project.id = :projectId
              and p.isNotice = false
              and (:cursorTime is null or p.createdAt < :cursorTime
                   or (p.createdAt = :cursorTime and p.id < :cursorId))
            order by p.createdAt desc, p.id desc
            """)
    List<Post> findFeedPage(
            @Param("projectId") Long projectId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
