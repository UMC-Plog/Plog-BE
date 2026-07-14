package com.plog.domain.evaluation.repository;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeerEvaluationRepository extends JpaRepository<PeerEvaluation, Long> {

    boolean existsByEvaluatorAndEvaluatee(ProjectMember evaluator, ProjectMember evaluatee);
}