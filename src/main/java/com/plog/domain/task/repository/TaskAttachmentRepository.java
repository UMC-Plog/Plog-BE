package com.plog.domain.task.repository;

import com.plog.domain.task.entity.TaskAttachment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findAllByTaskId(Long taskId);

    // 목록 조회에서 Task N개의 첨부파일을 한 번에 가져오기 위한 IN 조회.
    List<TaskAttachment> findAllByTaskIdInOrderByIdAsc(Collection<Long> taskIds);

    // 목록 조회는 첨부파일 개수만 필요 → count(*) 로 taskId별 집계.
    @Query("select ta.task.id as taskId, count(ta) as count "
            + "from TaskAttachment ta where ta.task.id in :taskIds "
            + "group by ta.task.id")
    List<TaskAttachmentCount> countByTaskIds(@Param("taskIds") List<Long> taskIds);

    interface TaskAttachmentCount {
        Long getTaskId();
        long getCount();
    }
}