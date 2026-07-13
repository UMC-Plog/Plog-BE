package com.plog.domain.project.repository;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {
    Optional<ProjectMember> findByProjectIdAndUserIdAndProjectStatus(
            String projectId,
            String userId,
            ProjectMemberStatus projectStatus
    );

    List<ProjectMember> findAllByProjectIdAndProjectStatusAndProjectMemberIdNot(
            String projectId,
            ProjectMemberStatus projectStatus,
            String projectMemberId
    );
}
