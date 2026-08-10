package com.plog.domain.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 도메인 0단계(수집). {@code PostActivityLogService}와 동일 패턴 —
 * source lock으로 동시성 방어 후 {@code existsBySourceDomainAndSourceRefId}로 멱등 체크.
 * <p>
 * TASK_STATUS_CHANGE/TASK_ATTACHMENT_ADD 둘 다 content가 없는 유형이라({@link ActivityClassificationRules}
 * 참고) content는 항상 null로 넘긴다. linkedTask는 원천이 이미 알고 있으므로 0단계에서 바로 채운다
 * ({@link ReportActivityLog#create(ProjectMember, SourceDomain, RawActivityType, String, LocalDateTime,
 * String, String, Task)} 오버로드).
 * <p>
 * metadata는 문자열 직접 조립 대신 {@link TaskStatusChangeMetadata}/{@link TaskAttachmentAddMetadata}
 * (schemaVersion 포함 타입 DTO)를 {@link ObjectMapper}로 직렬화한다 — 이미 생산자(여기)와
 * 소비자({@link ActivityClassificationRules})가 붙어 있어 스키마를 나중에 바꾸려면 기존 데이터
 * 마이그레이션이 필요해진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActivityLogService {
    private final ReportActivityLogRepository activityLogRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    /**
     * @param previousStatus 이전 상태. 정상 경로는 항상 채워서 부르고, 안전망 재수집 경로는
     *                        Task가 상태 이력을 저장하지 않아 null을 넘긴다({@link TaskStatusChangeMetadata} 참고)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectStatusChanged(
            Long taskId, Long memberId, TaskStatus previousStatus, TaskStatus newStatus, LocalDateTime occurredAt) {
        // 상태변경은 한 카드에 여러 번 일어나므로 occurredAt까지 포함해야 sourceRefId가 유니크하다.
        String sourceRefId = "task-status:" + taskId + ":" + occurredAt;
        String metadata = writeMetadata(TaskStatusChangeMetadata.of(taskId, previousStatus, newStatus));
        if (metadata == null) {
            return;
        }
        collect(memberId, taskId, RawActivityType.TASK_STATUS_CHANGE, occurredAt, sourceRefId, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectAttachmentAdded(Long attachmentId, Long taskId, Long memberId, LocalDateTime occurredAt) {
        String sourceRefId = "task-attachment:" + attachmentId;
        String metadata = writeMetadata(TaskAttachmentAddMetadata.of(taskId, attachmentId));
        if (metadata == null) {
            return;
        }
        collect(memberId, taskId, RawActivityType.TASK_ATTACHMENT_ADD, occurredAt, sourceRefId, metadata);
    }

    private void collect(
            Long memberId,
            Long taskId,
            RawActivityType activityType,
            LocalDateTime occurredAt,
            String sourceRefId,
            String metadata
    ) {
        activityLogRepository.acquireSourceLock(SourceDomain.TASK.name() + ":" + sourceRefId);
        if (activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.TASK, sourceRefId)) {
            return;
        }
        ProjectMember member = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("활동 로그의 프로젝트 멤버를 찾을 수 없습니다."));
        Task task = taskRepository.getReferenceById(taskId);
        activityLogRepository.save(ReportActivityLog.create(
                member, SourceDomain.TASK, activityType, null, occurredAt, metadata, sourceRefId, task));
    }

    /**
     * 직렬화 실패는 사실상 일어날 수 없다(레코드가 원시 타입/enum/Long뿐) — 그래도 이 한 건 때문에
     * REQUIRES_NEW 트랜잭션이 예외로 죽어 재시도 경로를 타게 하기보다, 로그만 남기고 이번 수집은
     * 건너뛴다(다음 배치/재수집 스케줄러가 다시 시도한다).
     */
    private String writeMetadata(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("task_activity_log_metadata_serialization_failed metadata={}", metadata, e);
            return null;
        }
    }
}