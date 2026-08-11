package com.plog.domain.evaluation.repository;

import com.plog.domain.evaluation.entity.SelfFeedback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelfFeedbackRepository extends JpaRepository<SelfFeedback, Long> {

    Optional<SelfFeedback> findByProjectMemberId(Long projectMemberId);

    @Query("""
            select count(selfFeedback)
            from SelfFeedback selfFeedback
            where selfFeedback.projectMember.project.id = :projectId
              and selfFeedback.projectMember.status = com.plog.domain.project.entity.MemberStatus.ACTIVE
            """)
    long countSubmittedByActiveProjectMembers(@Param("projectId") Long projectId);
}
