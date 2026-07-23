package com.plog.domain.post.repository;

import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.PostAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findAllByPostIdOrderByIdAsc(Long postId);
    List<PostAttachment> findAllByPostIdInOrderByIdAsc(Collection<Long> postIds);

    boolean existsByAttachmentTypeAndFileUrl(AttachmentType attachmentType, String fileUrl);

    void deleteAllByPostId(Long postId);
}
