package com.plog.domain.evaluation.repository;

import com.plog.domain.evaluation.entity.SelfFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfFeedbackRepository extends JpaRepository<SelfFeedback, Long> {

    Optional<SelfFeedback> findByProjectMemberId(Long projectMemberId);
}