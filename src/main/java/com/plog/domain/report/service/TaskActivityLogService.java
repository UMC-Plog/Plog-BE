package com.plog.domain.report.service;

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
 */
@Service
@RequiredArgsConstructor
public class TaskActivityLogService {
    private final ReportActivityLogRepository activityLogRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectStatusChanged(Long taskId, Long memberId, TaskStatus newStatus, LocalDateTime occurredAt) {
        // 상태변경은 한 카드에 여러 번 일어나므로 occurredAt까지 포함해야 sourceRefId가 유니크하다.
        String sourceRefId = "task-status:" + taskId + ":" + occurredAt;
        collect(memberId, taskId, RawActivityType.TASK_STATUS_CHANGE, occurredAt, sourceRefId,
                "{\"taskId\":" + taskId + ",\"newStatus\":\"" + newStatus.name() + "\"}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectAttachmentAdded(Long attachmentId, Long taskId, Long memberId, LocalDateTime occurredAt) {
        String sourceRefId = "task-attachment:" + attachmentId;
        collect(memberId, taskId, RawActivityType.TASK_ATTACHMENT_ADD, occurredAt, sourceRefId,
                "{\"taskId\":" + taskId + ",\"attachmentId\":" + attachmentId + "}");
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
}