package com.plog.domain.task.repository;

import com.plog.domain.task.entity.Task;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Task -> ProjectMember -> Project 로 이어지는 중첩 프로퍼티 조회.
    // Task에 project_id가 없으므로 목록 조회는 반드시 이 경로를 거쳐야 한다.
    // 담당자 닉네임을 같이 내려야 하므로 N+1 방지를 위해 projectMember를 fetch join.
    @EntityGraph(attributePaths = {"projectMember"})
    List<Task> findAllByProjectMember_Project_IdOrderByCreatedAtAsc(Long projectId);
}