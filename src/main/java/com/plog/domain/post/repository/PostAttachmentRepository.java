package com.plog.domain.post.repository;

import com.plog.domain.post.entity.PostAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findAllByPostIdOrderByIdAsc(Long postId);
    List<PostAttachment> findAllByPostIdInOrderByIdAsc(Collection<Long> postIds);
    void deleteAllByPostId(Long postId);

    @Query("select a.uploadedFile.id from PostAttachment a "
            + "where a.post.id = :postId and a.uploadedFile is not null")
    List<Long> findFileIdsByPostId(@Param("postId") Long postId);
}
