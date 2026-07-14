package com.plog.domain.project.repository;

import com.plog.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    // 프로젝트 내 멤버 조회
    List<ProjectMember> findAllByProjectId(Long projectId);

    // 특정 유저의 특정 프로젝트 멤버 정보 조회
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);
}