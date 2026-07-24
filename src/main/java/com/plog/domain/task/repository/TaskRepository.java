package com.plog.domain.task.repository;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Task -> ProjectMember -> Project 로 이어지는 중첩 프로퍼티 조회.
    // 담당자 닉네임 등을 함께 사용할 수 있도록 projectMember를 미리 조회
    // 담당자 닉네임을 같이 내려야 하므로 N+1 방지를 위해 projectMember를 fetch join.
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    List<Task> findAllByProjectMember_Project_IdOrderByCreatedAtAsc(Long projectId);

    // 상세 조회용 — taskId + projectId(경유 ProjectMember.Project) 소속까지 한 번에 검증.
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    Optional<Task> findByIdAndProjectMember_Project_Id(Long id, Long projectId);

    @Query("select task.projectMember.project.id as projectId, "
            + "count(task) as totalCount, "
            + "sum(case when task.cardStatus = :doneStatus then 1 else 0 end) as doneCount "
            + "from Task task where task.projectMember.project.id in :projectIds "
            + "group by task.projectMember.project.id")
    List<ProjectTaskProgress> findProgressByProjectIds(
            @Param("projectIds") List<Long> projectIds,
            @Param("doneStatus") TaskStatus doneStatus
    );

    // 첨부파일 개수 검증 + insert를 원자적으로 만들기 위한 락.
    // 같은 taskId에 대한 동시 등록 요청을 이 줄에서 직렬화한다 (하나가 커밋될 때까지 나머지는 대기).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from Task task where task.id = :taskId")
    Optional<Task> findByIdForUpdate(@Param("taskId") Long taskId);

    interface ProjectTaskProgress {
        Long getProjectId();
        long getTotalCount();
        long getDoneCount();
    }

    // 특정 담당자(ProjectMember) 기준 업무카드 조회
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    List<Task> findAllByProjectMember_IdOrderByCreatedAtAsc(Long projectMemberId);

    // 마감일이 지났고 완료(DONE)가 아닌 업무카드만 조회. 가장 오래전에 지난 카드부터 보여준다.
    @EntityGraph(attributePaths = {"projectMember", "projectMember.user"})
    @Query("select task from Task task "
            + "where task.projectMember.project.id = :projectId "
            + "and task.endDate < :today "
            + "and task.cardStatus <> :doneStatus "
            + "order by task.endDate asc")
    List<Task> findOverdueTasksByProjectId(
            @Param("projectId") Long projectId,
            @Param("today") LocalDate today,
            @Param("doneStatus") TaskStatus doneStatus
    );
}
