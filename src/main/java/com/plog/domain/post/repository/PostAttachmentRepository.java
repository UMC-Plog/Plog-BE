package com.plog.domain.post.repository;

import com.plog.domain.post.entity.PostAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /*
     * 다운로드 URL 발급용 단건 조회. uploadedFile(파일 키·원본 파일명)과
     * post → projectMember → project(권한 검사용 프로젝트 id)를 함께 쓰므로 fetch join 한다.
     * LINK 첨부는 uploadedFile 이 null 이라 left join 이어야 한다.
     */
    @Query("select a from PostAttachment a "
            + "left join fetch a.uploadedFile "
            + "join fetch a.post p "
            + "join fetch p.projectMember pm "
            + "join fetch pm.project "
            + "where a.id = :postAttachmentId")
    Optional<PostAttachment> findWithFileAndProjectById(
            @Param("postAttachmentId") Long postAttachmentId);
}
