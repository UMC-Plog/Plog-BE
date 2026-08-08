package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.projection.EmbeddingClaimProjection;
import com.plog.domain.report.repository.projection.EvaluationLogRecoveryTarget;
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
            SELECT report_activity_log_id AS id, content AS content
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

    // 위에서 선점한 행에 리스 만료 시각을 찍는다. selectClaimableEmbeddingActivities와 같은
    // 트랜잭션 안에서 호출해야 SKIP LOCKED로 잡은 락이 유효한 동안 반영된다.
    @Modifying
    @Query("update ReportActivityLog a set a.embeddingLeaseUntil = :leaseUntil where a.id in :ids")
    void leaseForEmbedding(@Param("ids") List<Long> ids, @Param("leaseUntil") LocalDateTime leaseUntil);

    // 4단계 정량계산에서 멤버별로 묶어서 집계할 때 사용
    List<ReportActivityLog> findByProjectMember_IdAndSourceDomainIn(
            Long projectMemberId, List<SourceDomain> sourceDomains);

    List<ReportActivityLog> findByProjectMember_Id(Long projectMemberId);
}