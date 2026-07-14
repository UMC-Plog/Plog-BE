package com.plog.domain.evaluation.repository;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface PeerEvaluationRepository extends JpaRepository<PeerEvaluation, Long> {

    @Query("SELECT p.evaluatee.id FROM PeerEvaluation p WHERE p.evaluator = :evaluator")
    Set<Long> findEvaluatedTargetIds(@Param("evaluator") ProjectMember evaluator);

}