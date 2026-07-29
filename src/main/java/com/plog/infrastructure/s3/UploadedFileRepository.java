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

    /** 안전망 대상 — Invoke 가 유실됐거나 재요청으로 되돌려진 행. */
    List<UploadedFile> findByThumbnailStatusAndThumbnailAtIsNull(
            ThumbnailStatus thumbnailStatus, Limit limit);

    /** 결과 확인 대상 — Invoke 후 일정 시간이 지난 행. */
    List<UploadedFile> findByThumbnailStatusAndThumbnailAtBefore(
            ThumbnailStatus thumbnailStatus, LocalDateTime threshold, Limit limit);

    /*
     * 아래 세 UPDATE 는 스케줄러가 S3/Lambda 호출을 트랜잭션 밖에서 하기 위해 필요하다.
     * markTagged 와 같은 이유다 — 배치 전체를 한 트랜잭션으로 묶으면 블로킹 네트워크
     * 호출 동안 DB 커넥션을 붙들고 있게 된다.
     */

    /*
     * 세 UPDATE 모두 WHERE 에 thumbnailStatus 가드를 둔다. id 만으로 쓰면 늦게 도착한
     * 갱신이 이미 확정된 행을 되돌린다 — 예를 들어 A 가 READY 로 올린 뒤 B 의 뒤늦은
     * recordThumbnailAttempt 가 도착하면, 썸네일이 실재하는데도 FAILED 로 못 박혀
     * 프론트가 원본 팬아웃으로 되돌아간다. UploadedFile 에 @Version 이 없어 가드가
     * 유일한 방어선이다. 호출자는 반환된 행 수를 보고 자기가 이겼는지 판단한다.
     */

    /**
     * 이 행의 Invoke 권리를 선점한다. thumbnailAt IS NULL 조건이 곧 락이라, 즉시 Invoke 와
     * 스케줄러 안전망이 같은 행을 동시에 집어도 한쪽만 1을 받는다(중복 Lambda 실행 방지).
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update UploadedFile f set f.thumbnailKey = :thumbnailKey, f.thumbnailAt = :at "
            + "where f.id = :id and f.thumbnailStatus = :pending and f.thumbnailAt is null")
    int markThumbnailRequested(@Param("id") Long id,
                               @Param("thumbnailKey") String thumbnailKey,
                               @Param("at") LocalDateTime at,
                               @Param("pending") ThumbnailStatus pending);

    /**
     * taggedAt 을 함께 비운다. UploadedFileTagScheduler 를 한 번 더 태워
     * 썸네일 키에도 원본과 같은 state 태그를 붙이기 위해서다. 이게 없으면 원본이
     * ORPHANED 로 지워질 때 썸네일만 영구히 남는다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update UploadedFile f set f.thumbnailStatus = :ready, f.taggedAt = null "
            + "where f.id = :id and f.thumbnailStatus = :pending")
    int markThumbnailReady(@Param("id") Long id,
                           @Param("ready") ThumbnailStatus ready,
                           @Param("pending") ThumbnailStatus pending);

    /**
     * Invoke 1회가 무위로 끝났음을 기록하고 재요청 상태(thumbnailAt = null)로 되돌린다.
     * <b>폴링 1회가 아니라 Invoke 1회를 센다</b> — 호출자가 타임아웃을 판정한 뒤에만 부른다.
     * 상한을 넘으면 FAILED 로 못 박아 무한 재시도를 끊는다.
     * <p>
     * 증가와 상한 판정을 한 UPDATE 로 묶어야 두 틱이 겹쳐도 카운트가 어긋나지 않는다.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update UploadedFile f set f.thumbnailAttempts = f.thumbnailAttempts + 1, "
            + "f.thumbnailAt = null, "
            + "f.thumbnailStatus = case when f.thumbnailAttempts + 1 > :maxAttempts "
            + "then :failed else f.thumbnailStatus end "
            + "where f.id = :id and f.thumbnailStatus = :pending")
    int recordThumbnailAttempt(@Param("id") Long id,
                               @Param("maxAttempts") int maxAttempts,
                               @Param("failed") ThumbnailStatus failed,
                               @Param("pending") ThumbnailStatus pending);
}
