package com.plog.domain.task.repository;

import com.plog.domain.task.entity.TaskAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findAllByTaskId(Long taskId);

}