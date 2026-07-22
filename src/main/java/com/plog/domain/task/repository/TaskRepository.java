package com.plog.domain.task.repository;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Task -> ProjectMember -> Project 로 이어지는 중첩 프로퍼티 조회.
    // 특정 프로젝트의 업무카드 목록 조회
    // 담당자 닉네임 등을 함께 사용할 수 있도록 projectMember를 미리 조회
    // 담당자 닉네임을 같이 내려야 하므로 N+1 방지를 위해 projectMember를 fetch join.
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    List<Task> findAllByProjectMember_Project_IdOrderByCreatedAtAsc(Long projectId);

    @Query("select task.projectMember.project.id as projectId, "
            + "count(task) as totalCount, "
            + "sum(case when task.cardStatus = :doneStatus then 1 else 0 end) as doneCount "
            + "from Task task where task.projectMember.project.id in :projectIds "
            + "group by task.projectMember.project.id")
    List<ProjectTaskProgress> findProgressByProjectIds(
            @Param("projectIds") List<Long> projectIds,
            @Param("doneStatus") TaskStatus doneStatus
    );

    interface ProjectTaskProgress {
        Long getProjectId();

        long getTotalCount();

        long getDoneCount();
    }
}
