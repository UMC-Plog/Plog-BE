package com.plog.domain.task.repository;

import com.plog.domain.task.entity.TaskStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
    List<TaskStatusHistory> findAllByTaskProjectMemberProjectIdOrderByOccurredAtAscIdAsc(Long projectId);
}
