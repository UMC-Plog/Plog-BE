package com.plog.domain.evaluation.repository;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.List;

public interface PeerEvaluationRepository extends JpaRepository<PeerEvaluation, Long> {

    @Query("SELECT p.evaluatee.id FROM PeerEvaluation p WHERE p.evaluator = :evaluator")
    Set<Long> findEvaluatedTargetIds(@Param("evaluator") ProjectMember evaluator);

    Optional<PeerEvaluation> findByEvaluatorIdAndEvaluateeId(Long evaluatorId, Long evaluateeId);

    List<PeerEvaluation> findAllByEvaluateeProjectId(Long projectId);

    /** 멤버 1명이 받은 평가 전부. Peer 집계(평균·역량점수·키워드·평가자 수) 계산에 쓴다. */
    List<PeerEvaluation> findAllByEvaluateeId(Long evaluateeId);

    @Query("""
            select count(peerEvaluation)
            from PeerEvaluation peerEvaluation
            where peerEvaluation.evaluator.project.id = :projectId
              and peerEvaluation.evaluatee.project.id = :projectId
              and peerEvaluation.evaluator.status = com.plog.domain.project.entity.MemberStatus.ACTIVE
              and peerEvaluation.evaluatee.status = com.plog.domain.project.entity.MemberStatus.ACTIVE
            """)
    long countSubmittedByActiveProjectMembers(@Param("projectId") Long projectId);

}
