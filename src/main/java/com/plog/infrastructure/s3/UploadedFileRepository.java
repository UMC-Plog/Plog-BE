package com.plog.infrastructure.s3;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    Optional<UploadedFile> findByFileKey(String fileKey);

    /**
     * PENDING 인 행만 CONFIRMED 로 옮긴다. 반환값 0 이면 이미 다른 첨부가 선점한 키다.
     * <p>
     * 전역 중복 참조를 막는 실제 메커니즘이 이 원자적 UPDATE 다. FK 는 다대일이라
     * UNIQUE(file_key) 만으로는 여러 도메인 행이 같은 파일을 가리키는 것을 못 막는다.
     */
    /*
     * clearAutomatically 를 켜면 안 된다. 이 메서드는 호출자의 트랜잭션에 참여하므로
     * EntityManager 가 공유되고, clear() 는 그 컨텍스트의 모든 프록시를 세션에서 떼어낸다.
     * PostService.updatePost 처럼 Post 를 잡고 있다가 뒤에서 지연 로딩하는 호출자가
     * LazyInitializationException 으로 죽는다. 대신 성공 후 UploadedFile#confirm 으로
     * 메모리 상태를 DB 와 맞춘다.
     */
    @Modifying(flushAutomatically = true)
    @Query("update UploadedFile f set f.status = :confirmed, f.confirmedAt = :now, f.taggedAt = null "
            + "where f.fileKey = :fileKey and f.status = :pending")
    int confirmIfPending(@Param("fileKey") String fileKey,
                         @Param("now") LocalDateTime now,
                         @Param("confirmed") UploadedFileStatus confirmed,
                         @Param("pending") UploadedFileStatus pending);

    /**
     * 방치된 PENDING 을 조건부로 회수한다. select 후 dirty checking 으로 바꾸면
     * 그 사이에 확정된 행까지 ORPHANED 로 덮어써서 #117 을 되살린다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UploadedFile f set f.status = :orphaned, f.releasedAt = :now, f.taggedAt = null "
            + "where f.status = :pending and f.issuedAt < :threshold")
    int releaseAbandonedPending(@Param("now") LocalDateTime now,
                                @Param("threshold") LocalDateTime threshold,
                                @Param("orphaned") UploadedFileStatus orphaned,
                                @Param("pending") UploadedFileStatus pending);

    List<UploadedFile> findByTaggedAtIsNull(Limit limit);

    /**
     * 태깅 성공을 한 건씩 기록한다. 스케줄러가 S3 호출을 트랜잭션 밖에서 하기 위해
     * 필요하다 — 배치 전체를 한 트랜잭션으로 묶으면 최대 200회의 블로킹 네트워크
     * 호출 동안 DB 커넥션을 붙들고 있게 된다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update UploadedFile f set f.taggedAt = :taggedAt where f.id = :id")
    int markTagged(@Param("id") Long id, @Param("taggedAt") LocalDateTime taggedAt);

    List<UploadedFile> findByStatusAndIssuedAtBefore(UploadedFileStatus status,
                                                     LocalDateTime threshold, Limit limit);

    List<UploadedFile> findByStatusAndReleasedAtBefore(UploadedFileStatus status,
                                                       LocalDateTime threshold, Limit limit);
}
