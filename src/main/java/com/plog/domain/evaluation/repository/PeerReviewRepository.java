package com.plog.domain.evaluation.repository;

import com.plog.domain.evaluation.entity.PeerReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeerReviewRepository extends JpaRepository<PeerReview, String> {
    boolean existsByProjectIdAndEvaluatorIdAndEvaluateeId(
            String projectId,
            String evaluatorId,
            String evaluateeId
    );
}
