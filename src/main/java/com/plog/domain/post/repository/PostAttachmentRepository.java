package com.plog.domain.post.repository;

import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.PostAttachment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findAllByPostIdOrderByIdAsc(Long postId);
    List<PostAttachment> findAllByPostIdInOrderByIdAsc(Collection<Long> postIds);

    @Query("""
            select distinct attachment.fileUrl
            from PostAttachment attachment
            where attachment.attachmentType = :attachmentType
              and attachment.fileUrl in :fileUrls
            """)
    List<String> findReferencedFileUrls(
            @Param("attachmentType") AttachmentType attachmentType,
            @Param("fileUrls") Collection<String> fileUrls
    );

    void deleteAllByPostId(Long postId);
}
