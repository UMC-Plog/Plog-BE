package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.projection.EvaluationLogRecoveryTarget;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<ReportActivityLog> findBySourceDomainInAndNoiseFilteredIsNull(List<SourceDomain> sourceDomains, Limit limit);

    // 4단계 정량계산에서 멤버별로 묶어서 집계할 때 사용
    List<ReportActivityLog> findByProjectMember_IdAndSourceDomainIn(
            Long projectMemberId, List<SourceDomain> sourceDomains);

    List<ReportActivityLog> findByProjectMember_Id(Long projectMemberId);
}