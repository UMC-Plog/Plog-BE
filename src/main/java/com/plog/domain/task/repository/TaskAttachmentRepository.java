package com.plog.domain.task.repository;

import com.plog.domain.task.entity.TaskAttachment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {

    List<TaskAttachment> findAllByTaskId(Long taskId);

    // 목록 조회에서 Task N개의 첨부파일을 한 번에 가져오기 위한 IN 조회.
    List<TaskAttachment> findAllByTaskIdInOrderByIdAsc(Collection<Long> taskIds);

    // 삭제 대상 첨부파일이 실제로 그 taskId 소속인지까지 조건에 걸어서 조회 (소속 검증 겸용)
    Optional<TaskAttachment> findByIdAndTaskId(Long id, Long taskId);

    // 목록 조회는 첨부파일 개수만 필요 → count(*) 로 taskId별 집계.
    @Query("select ta.task.id as taskId, count(ta) as count "
            + "from TaskAttachment ta where ta.task.id in :taskIds "
            + "group by ta.task.id")

    List<TaskAttachmentCount> countByTaskIds(@Param("taskIds") List<Long> taskIds);

    // 전체 목록을 안 가져오고 개수만 센다 (COUNT(*) 쿼리 1번)
    long countByTaskId(Long taskId);

    @Query("select a.uploadedFile.id from TaskAttachment a "
            + "where a.task.id = :taskId and a.uploadedFile is not null")
    List<Long> findFileIdsByTaskId(@Param("taskId") Long taskId);

    /*
     * 다운로드 URL 발급용 단건 조회. uploadedFile(파일 키·원본 파일명)과
     * task → projectMember → project(권한 검사용 프로젝트 id)를 함께 쓰므로 fetch join 한다.
     * LINK 첨부는 uploadedFile 이 null 이라 left join 이어야 한다.
     */
    @Query("select a from TaskAttachment a "
            + "left join fetch a.uploadedFile "
            + "join fetch a.task t "
            + "join fetch t.projectMember pm "
            + "join fetch pm.project "
            + "where a.id = :taskAttachmentId")
    Optional<TaskAttachment> findWithFileAndProjectById(
            @Param("taskAttachmentId") Long taskAttachmentId);

    interface TaskAttachmentCount {
        Long getTaskId();
        long getCount();
    }
}