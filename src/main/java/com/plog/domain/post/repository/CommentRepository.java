package com.plog.domain.post.repository;

import com.plog.domain.post.entity.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByPostIdOrderByCreatedAtAscIdAsc(Long postId);
    Optional<Comment> findByIdAndPostId(Long id, Long postId);
    long countByPostId(Long postId);
}
