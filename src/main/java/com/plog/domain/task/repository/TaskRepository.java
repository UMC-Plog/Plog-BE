package com.plog.domain.task.repository;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

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
