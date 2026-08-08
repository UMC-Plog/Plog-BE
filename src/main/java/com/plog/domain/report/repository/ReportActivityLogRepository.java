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

    // 3단계 임베딩 대상 조회용 — 정제를 통과했고(noiseFiltered=false) 아직 임베딩 처리 전인(embeddingModel이
    // null인) 행. embeddingModel은 실제 모델명 또는 ReportActivityLog.EMBEDDING_NOT_APPLICABLE로 채워지므로
    // null은 "처리 전"만을 뜻한다 — 임베딩할 텍스트가 없는 행도 한 번 처리되면 다시 선택되지 않는다.
    List<ReportActivityLog> findByNoiseFilteredFalseAndEmbeddingModelIsNullOrderByOccurredAtAscIdAsc(Limit limit);

    // 4단계 정량계산에서 멤버별로 묶어서 집계할 때 사용
    List<ReportActivityLog> findByProjectMember_IdAndSourceDomainIn(
            Long projectMemberId, List<SourceDomain> sourceDomains);

    List<ReportActivityLog> findByProjectMember_Id(Long projectMemberId);
}