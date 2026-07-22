package com.plog.domain.post.repository;

import com.plog.domain.post.entity.PostLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPostIdAndProjectMemberId(Long postId, Long projectMemberId);
    boolean existsByPostIdAndProjectMemberId(Long postId, Long projectMemberId);
    long countByPostId(Long postId);

    @Modifying
    @Query(value = """
            insert into likes (post_id, project_member_id, created_at, updated_at)
            values (:postId, :projectMemberId, now(), now())
            on conflict (post_id, project_member_id) do nothing
            """, nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("projectMemberId") Long projectMemberId);
}
