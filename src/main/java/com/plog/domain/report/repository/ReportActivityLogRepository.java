package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.projection.EmbeddingClaimProjection;
import com.plog.domain.report.repository.projection.PostLogRecoveryTarget;
import com.plog.domain.report.repository.projection.CommentLogRecoveryTarget;
import com.plog.domain.report.repository.projection.EvaluationLogRecoveryTarget;
import com.plog.domain.report.repository.projection.TaskAttachmentLogRecoveryTarget;
import com.plog.domain.report.repository.projection.TaskStatusLogRecoveryTarget;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportActivityLogRepository extends JpaRepository<ReportActivityLog, Long> {

    @Query(value = "select pg_advisory_xact_lock(hashtextextended(:sourceKey, 0))", nativeQuery = true)
    void acquireSourceLock(@Param("sourceKey") String sourceKey);

    boolean existsBySourceDomainAndSourceRefId(SourceDomain sourceDomain, String sourceRefId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO report_activity_log (
                project_member_id,
                source_domain,
                raw_activity_type,
                content,
                occurred_at,
                metadata,
                source_ref_id,
                created_at,
                updated_at
            )
            VALUES (
                :projectMemberId,
                :sourceDomain,
                :rawActivityType,
                :content,
                :occurredAt,
                CAST(:metadata AS jsonb),
                :sourceRefId,
                current_timestamp,
                current_timestamp
            )
            ON CONFLICT (source_domain, source_ref_id) DO UPDATE
            SET project_member_id = EXCLUDED.project_member_id,
                raw_activity_type = EXCLUDED.raw_activity_type,
                content = EXCLUDED.content,
                occurred_at = EXCLUDED.occurred_at,
                metadata = EXCLUDED.metadata,
                updated_at = current_timestamp
            WHERE report_activity_log.project_member_id IS DISTINCT FROM EXCLUDED.project_member_id
               OR report_activity_log.raw_activity_type IS DISTINCT FROM EXCLUDED.raw_activity_type
               OR report_activity_log.content IS DISTINCT FROM EXCLUDED.content
               OR report_activity_log.occurred_at IS DISTINCT FROM EXCLUDED.occurred_at
               OR report_activity_log.metadata IS DISTINCT FROM EXCLUDED.metadata
            """, nativeQuery = true)
    int upsertExternalActivityLog(
            @Param("projectMemberId") Long projectMemberId,
            @Param("sourceDomain") String sourceDomain,
            @Param("rawActivityType") String rawActivityType,
            @Param("content") String content,
            @Param("occurredAt") LocalDateTime occurredAt,
            @Param("metadata") String metadata,
            @Param("sourceRefId") String sourceRefId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM report_activity_log
            WHERE source_domain = :sourceDomain
              AND source_ref_id = :sourceRefId
            """, nativeQuery = true)
    int deleteExternalActivityLog(
            @Param("sourceDomain") String sourceDomain,
            @Param("sourceRefId") String sourceRefId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM report_activity_log
            WHERE project_member_id = :projectMemberId
              AND source_domain = :sourceDomain
              AND starts_with(source_ref_id, :sourceRefPrefix)
            """, nativeQuery = true)
    int deleteExternalActivityLogsByMemberAndSourcePrefix(
            @Param("projectMemberId") Long projectMemberId,
            @Param("sourceDomain") String sourceDomain,
            @Param("sourceRefPrefix") String sourceRefPrefix
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM report_activity_log
            WHERE source_domain = :sourceDomain
              AND starts_with(source_ref_id, :sourceRefPrefix)
            """, nativeQuery = true)
    int deleteExternalActivityLogsBySourcePrefix(
            @Param("sourceDomain") String sourceDomain,
            @Param("sourceRefPrefix") String sourceRefPrefix
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from ReportActivityLog log where log.linkedTask.id = :taskId")
    int deleteAllByLinkedTaskId(@Param("taskId") Long taskId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ReportActivityLog log "
            + "where log.sourceDomain = :sourceDomain and log.sourceRefId = :sourceRefId")
    int deleteBySourceDomainAndSourceRefId(
            @Param("sourceDomain") SourceDomain sourceDomain,
            @Param("sourceRefId") String sourceRefId
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM report_activity_log
            WHERE source_domain = 'POST'
              AND (source_ref_id = CONCAT('post:', CAST(:postId AS text))
                   OR metadata ->> 'postId' = CAST(:postId AS text))
            """, nativeQuery = true)
    int deletePostAndCommentActivities(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE report_activity_log
            SET content = :content,
                metadata = CAST(:metadata AS jsonb),
                noise_filtered = NULL,
                classified_type = NULL,
                embedding_model = NULL,
                embedding = NULL,
                embedding_lease_until = NULL,
                embedding_lease_token = NULL,
                classification_retry_count = 0,
                classification_next_retry_at = NULL,
                classification_failed = false,
                updated_at = current_timestamp
            WHERE source_domain = :sourceDomain
              AND source_ref_id = :sourceRefId
              AND (content IS DISTINCT FROM :content
                   OR metadata IS DISTINCT FROM CAST(:metadata AS jsonb))
            """, nativeQuery = true)
    int refreshSourceSnapshot(
            @Param("sourceDomain") String sourceDomain,
            @Param("sourceRefId") String sourceRefId,
            @Param("content") String content,
            @Param("metadata") String metadata
    );

    // 안전망 재수집 대상 조회 — AFTER_COMMIT 리스너가 유실해 아직 활동 로그가 없는 제출.
    // sourceRefId 규칙("peer-evaluation:"+id, "self-feedback:"+id)은 EvaluationActivityLogService의
    // 적재 규칙과 짝을 이룬다. threshold보다 오래된 행만 잡아 정상 비동기 처리 중인 건과 겹치지 않게 한다.
    @Query("""
            select pe.id as id, pe.createdAt as occurredAt
            from PeerEvaluation pe
            where pe.createdAt < :threshold
              and not exists (
                select 1 from ReportActivityLog r
                where r.sourceDomain = com.plog.domain.report.entity.SourceDomain.EVALUATION
                  and r.sourceRefId = concat('peer-evaluation:', cast(pe.id as string)))
            """)
    List<EvaluationLogRecoveryTarget> findPeerEvaluationsMissingActivityLog(
            @Param("threshold") LocalDateTime threshold, Limit limit);

    @Query("""
            select sf.id as id, sf.createdAt as occurredAt
            from SelfFeedback sf
            where sf.createdAt < :threshold
              and not exists (
                select 1 from ReportActivityLog r
                where r.sourceDomain = com.plog.domain.report.entity.SourceDomain.EVALUATION
                  and r.sourceRefId = concat('self-feedback:', cast(sf.id as string)))
            """)
    List<EvaluationLogRecoveryTarget> findSelfFeedbacksMissingActivityLog(
            @Param("threshold") LocalDateTime threshold, Limit limit);

    @Query("""
            select post.id as postId, post.projectMember.id as memberId,
                   post.content as content, post.createdAt as occurredAt
            from Post post
            where post.createdAt < :threshold
              and not exists (select 1 from ReportActivityLog log
                where log.sourceDomain = com.plog.domain.report.entity.SourceDomain.POST
                  and log.sourceRefId = concat('post:', cast(post.id as string)))
            """)
    List<PostLogRecoveryTarget> findPostsMissingActivityLog(
            @Param("threshold") LocalDateTime threshold, Limit limit);

    @Query("""
            select comment.id as commentId, comment.post.id as postId,
                   comment.projectMember.id as memberId, comment.content as content,
                   comment.createdAt as occurredAt
            from Comment comment
            where comment.createdAt < :threshold
              and not exists (select 1 from ReportActivityLog log
                where log.sourceDomain = com.plog.domain.report.entity.SourceDomain.POST
                  and log.sourceRefId = concat('comment:', cast(comment.id as string)))
            """)
    List<CommentLogRecoveryTarget> findCommentsMissingActivityLog(
            @Param("threshold") LocalDateTime threshold, Limit limit);

    // Task 상태변경(DONE 전이) 안전망 재수집 대상 조회.
    // TASK_STATUS_CHANGE의 sourceRefId엔 occurredAt이 문자열로 포함돼 있다(같은 카드가
    // DONE↔해제를 오가면 taskId만으로는 유니크하지 않아 시각까지 넣었다). 그래서 Evaluation
    // 재수집처럼 sourceRefId를 JPQL에서 문자열로 재구성하면 DB 타임스탬프 포맷과 Java
    // LocalDateTime#toString()이 어긋날 위험이 있다 — 대신 sourceRefId를 재구성하지 않고
    // linkedTask+occurredAt 값 자체로 "이미 적재됐는지"를 판정한다.
    // TaskStatusService가 DONE 전이 시 completedAt을 그대로 이벤트의 occurredAt으로 재사용하므로
    // (별도로 TimeUtil.now()를 다시 부르지 않음) 이 값이 정확히 일치한다.
    // 비-DONE 전이(TODO↔IN_PROGRESS)는 Task에 전이 시각을 담는 컬럼이 없어 재수집 대상에서
    // 제외한다 — updatedAt은 상태 변경이 아닌 다른 필드 수정에도 갱신돼 신뢰할 수 없다.
    @Query("""
            select t.id as taskId, t.projectMember.id as memberId, t.completedAt as occurredAt
            from Task t
            where t.cardStatus = com.plog.domain.task.entity.TaskStatus.DONE
              and t.completedAt is not null
              and t.completedAt < :threshold
              and not exists (
                select 1 from ReportActivityLog r
                where r.sourceDomain = com.plog.domain.report.entity.SourceDomain.TASK
                  and r.rawActivityType = com.plog.domain.report.entity.RawActivityType.TASK_STATUS_CHANGE
                  and r.linkedTask = t
                  and r.occurredAt = t.completedAt)
            """)
    List<TaskStatusLogRecoveryTarget> findDoneTasksMissingActivityLog(
            @Param("threshold") LocalDateTime threshold, Limit limit);

    // Task 첨부 안전망 재수집 대상 조회. sourceRefId가 attachmentId만으로 구성돼(타임스탬프 없음)
    // Evaluation 재수집과 동일하게 JPQL에서 안전하게 재구성할 수 있다.
    @Query("""
            select ta.id as attachmentId, ta.task.id as taskId, ta.task.projectMember.id as memberId,
                   ta.createdAt as occurredAt
            from TaskAttachment ta
            where ta.createdAt < :threshold
              and not exists (
                select 1 from ReportActivityLog r
                where r.sourceDomain = com.plog.domain.report.entity.SourceDomain.TASK
                  and r.sourceRefId = concat('task-attachment:', cast(ta.id as string)))
            """)
    List<TaskAttachmentLogRecoveryTarget> findAttachmentsMissingActivityLog(
            @Param("threshold") LocalDateTime threshold, Limit limit);

    // 1단계 정제 대상 조회용 — 아직 정제를 거치지 않은 내부 도메인 행.
    // limit은 ActivityRefinementService가 한 배치에서 처리할 상한(EvaluationActivityLogRecoveryScheduler와 동일한 관례).
    // occurredAt만으로는 동시각 행 사이의 순서가 불안정해 id를 안정적인 tie-breaker로 더한다 —
    // 배치 내 "가장 먼저 온 것"만 살리는 중복 판정이 호출마다 같은 순서를 보게 하기 위함이다.
    List<ReportActivityLog> findBySourceDomainInAndNoiseFilteredIsNullOrderByOccurredAtAscIdAsc(
            List<SourceDomain> sourceDomains, Limit limit);

    // 배치 경계를 넘는 중복 판정용. 이전 배치에서 이미 noiseFiltered=false로 확정된 동일 원문이
    // 있는지 확인한다(같은 회원·같은 도메인·id가 더 작은 = 더 먼저 처리된 행 한정).
    // cleanContent를 별도 컬럼으로 저장하지 않으므로 원문(content) 그대로 비교한다 — 공백 등
    // 사소한 표기 차이로 인한 미탐은 감수하되, 과탐(정상 문장을 잘못 노이즈 처리)은 피한다.
    boolean existsByProjectMember_IdAndSourceDomainAndContentAndNoiseFilteredFalseAndIdLessThan(
            Long projectMemberId, SourceDomain sourceDomain, String content, Long id);

    // 3단계 임베딩 대상 원자적 선점(claim)용. FOR UPDATE SKIP LOCKED로 동시에 여러 배치가 호출돼도
    // 같은 행을 중복으로 집어가지 않는다. embedding_lease_until이 비었거나 이미 만료된 행만 대상 —
    // 즉 처리 중 앱이 죽어도 리스가 풀리면 자동으로 다시 선점 대상이 된다(복구 가능).
    // 엔티티 전체가 아니라 id/content만 반환해서 이 시점엔 커넥션을 짧게만 잡는다.
    @Query(value = """
            SELECT report_activity_log_id AS id,
                   content AS content,
                   source_domain AS "sourceDomain",
                   source_ref_id AS "sourceRefId"
            FROM report_activity_log
            WHERE noise_filtered = false
              AND embedding_model IS NULL
              AND (embedding_lease_until IS NULL OR embedding_lease_until < :now)
            ORDER BY occurred_at ASC, report_activity_log_id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmbeddingClaimProjection> selectClaimableEmbeddingActivities(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    // 위에서 선점한 행에 리스 만료 시각 + 소유권 토큰을 찍는다. selectClaimableEmbeddingActivities와
    // 같은 트랜잭션 안에서 호출해야 SKIP LOCKED로 잡은 락이 유효한 동안 반영된다.
    @Modifying
    @Query("""
            update ReportActivityLog a
            set a.embeddingLeaseUntil = :leaseUntil, a.embeddingLeaseToken = :leaseToken
            where a.id in :ids
            """)
    void leaseForEmbedding(
            @Param("ids") List<Long> ids,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("leaseToken") String leaseToken);

    // 429 등으로 배치를 중단할 때, 현재 배치가 아직 처리 안 한 행의 리스를 즉시 풀어준다.
    // leaseToken이 일치하는 행만 건드린다 — 이미 만료돼 다른 실행자가 새로 선점한 행을
    // 실수로 같이 풀어버리지 않기 위한 낙관적 동시성 체크.
    @Modifying
    @Query("""
            update ReportActivityLog a
            set a.embeddingLeaseUntil = null, a.embeddingLeaseToken = null
            where a.id in :ids and a.embeddingLeaseToken = :leaseToken
            """)
    void releaseEmbeddingLease(@Param("ids") List<Long> ids, @Param("leaseToken") String leaseToken);

    // 배치 처리가 오래 걸려 리스가 도중에 만료될 위험이 있을 때, 아직 처리 안 한 행의 리스를
    // 연장한다. leaseToken이 일치하는 행만 건드린다(release와 같은 이유).
    @Modifying
    @Query("""
            update ReportActivityLog a
            set a.embeddingLeaseUntil = :leaseUntil
            where a.id in :ids and a.embeddingLeaseToken = :leaseToken
            """)
    void renewEmbeddingLease(
            @Param("ids") List<Long> ids,
            @Param("leaseToken") String leaseToken,
            @Param("leaseUntil") LocalDateTime leaseUntil);

    // 2단계 분류 대상 조회용 — 3단계(임베딩)가 끝났고(embeddingModel이 채워짐, 실제 모델명이든
    // N/A sentinel이든) 아직 분류되지 않은(classifiedType IS NULL) 내부 도메인 행.
    // classificationFailed=true(최대 재시도 초과로 영구 실패 확정)인 행과, classificationNextRetryAt이
    // 아직 미래인(backoff 대기 중인) 행은 제외한다 — 이 두 조건이 없으면 오래된 실패 행 하나가
    // occurredAt ASC 정렬상 계속 맨 앞을 차지해서 Limit을 그 행 재시도로만 소모하고, 뒤에 쌓인
    // 정상 행은 영영 배치에 못 들어오는 문제가 생긴다.
    // "IS NULL OR <= :now" 같은 OR-with-AND 조합은 메서드 이름 파생 쿼리로는 괄호 우선순위를
    // 보장할 수 없어(다른 AND 조건과 뒤섞여 잘못 묶일 위험) 명시적 JPQL로 뺐다.
    // 정렬 관례는 1단계 정제 조회와 동일: occurredAt만으로는 동시각 행 사이의 순서가 불안정해
    // id를 안정적인 tie-breaker로 더한다.
    @Query("""
            select a from ReportActivityLog a
            where a.sourceDomain in :sourceDomains
              and a.noiseFiltered = false
              and a.embeddingModel is not null
              and a.classifiedType is null
              and a.classificationFailed = false
              and (a.classificationNextRetryAt is null or a.classificationNextRetryAt <= :now)
            order by a.occurredAt asc, a.id asc
            """)
    List<ReportActivityLog> findClassificationTargets(
            @Param("sourceDomains") List<SourceDomain> sourceDomains,
            @Param("now") LocalDateTime now,
            Limit limit);

    @Query("""
            select log from ReportActivityLog log
              join fetch log.projectMember member
            where member.id in :projectMemberIds
              and member.status = com.plog.domain.project.entity.MemberStatus.ACTIVE
              and log.sourceDomain in :sourceDomains
            order by log.occurredAt desc, log.id desc
            """)
    List<ReportActivityLog> findExternalLogsForActiveProjectMembers(
            @Param("projectMemberIds") List<Long> projectMemberIds,
            @Param("sourceDomains") List<SourceDomain> sourceDomains
    );

    @Query("""
            select log from ReportActivityLog log join fetch log.projectMember member
            where member.id in :projectMemberIds
              and member.status = com.plog.domain.project.entity.MemberStatus.ACTIVE
              and log.sourceDomain in :sourceDomains
              and log.occurredAt <= :snapshotAt
            order by log.occurredAt desc, log.id desc
            """)
    List<ReportActivityLog> findExternalLogsForActiveProjectMembersAt(
            @Param("projectMemberIds") List<Long> projectMemberIds,
            @Param("sourceDomains") List<SourceDomain> sourceDomains,
            @Param("snapshotAt") LocalDateTime snapshotAt);

    List<ReportActivityLog> findByProjectMember_Id(Long projectMemberId);

    List<ReportActivityLog> findByProjectMember_IdAndOccurredAtLessThanEqual(
            Long projectMemberId, LocalDateTime snapshotAt);

    @Query("""
            select count(log) from ReportActivityLog log
            where log.projectMember.project.id = :projectId
              and log.occurredAt <= :snapshotAt
              and log.sourceDomain in :sourceDomains
              and (log.noiseFiltered is null
                or (log.noiseFiltered = false and log.embeddingModel is null)
                or (log.noiseFiltered = false and log.embeddingModel is not null
                    and log.classifiedType is null and log.classificationFailed = false))
            """)
    long countPendingReportActivities(
            @Param("projectId") Long projectId,
            @Param("snapshotAt") LocalDateTime snapshotAt,
            @Param("sourceDomains") List<SourceDomain> sourceDomains);
}
