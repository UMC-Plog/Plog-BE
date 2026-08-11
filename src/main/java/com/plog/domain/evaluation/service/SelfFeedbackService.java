package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.dto.request.SelfFeedbackCreateRequest;
import com.plog.domain.evaluation.dto.response.SelfFeedbackResponse;
import com.plog.domain.evaluation.dto.response.SelfFeedbackUpdateResponse;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.event.SelfFeedbackSubmittedEvent;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.event.EvaluationCompletionCheckRequestedEvent;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelfFeedbackService {

    private final SelfFeedbackRepository selfFeedbackRepository;
    private final EvaluationParticipantResolver participantResolver;
    private final ReportActivityLogRepository reportActivityLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SelfFeedbackResponse getMySelfFeedback(Long projectId, Long userId) {

        ProjectMember projectMember = participantResolver.requireEvaluator(projectId, userId);

        SelfFeedback selfFeedback = selfFeedbackRepository.findByProjectMemberId(projectMember.getId())
                .orElseThrow(() -> new ApiException(EvaluationErrorCode.SELF_FEEDBACK_NOT_FOUND));

        return new SelfFeedbackResponse(selfFeedback.getId(), selfFeedback.getContent());
    }

    @Transactional
    public SelfFeedbackResponse createSelfFeedback(Long projectId, Long userId, SelfFeedbackCreateRequest request) {
        ProjectMember projectMember = participantResolver.requireEvaluator(projectId, userId);

        if (!projectMember.getProject().isEvaluatingState(TimeUtil.todayUtc())) {
            throw new ApiException(EvaluationErrorCode.NOT_EVALUATING_STATE);
        }

        if (selfFeedbackRepository.findByProjectMemberId(projectMember.getId()).isPresent()) {
            throw new ApiException(EvaluationErrorCode.ALREADY_SUBMITTED_SELF_FEEDBACK);
        }

        SelfFeedback selfFeedback = SelfFeedback.builder()
                .projectMember(projectMember)
                .content(request.content())
                .build();

        try {
            selfFeedbackRepository.saveAndFlush(selfFeedback);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(EvaluationErrorCode.ALREADY_SUBMITTED_SELF_FEEDBACK);
        }
        eventPublisher.publishEvent(new SelfFeedbackSubmittedEvent(
                selfFeedback.getId(), projectMember.getId(), LocalDateTime.now(ZoneOffset.UTC)));
        eventPublisher.publishEvent(new EvaluationCompletionCheckRequestedEvent(projectId));

        return new SelfFeedbackResponse(selfFeedback.getId(), selfFeedback.getContent());
    }

    @Transactional
    public SelfFeedbackUpdateResponse updateSelfFeedback(Long projectId, Long userId, SelfFeedbackCreateRequest request) {
        ProjectMember projectMember = participantResolver.requireEvaluator(projectId, userId);

        if (projectMember.getProject().isCompleted()) {
            throw new ApiException(EvaluationErrorCode.CANNOT_MODIFY_FEEDBACK_AFTER_PUBLISH);
        }

        SelfFeedback selfFeedback = selfFeedbackRepository.findByProjectMemberId(projectMember.getId())
                .orElseThrow(() -> new ApiException(EvaluationErrorCode.SELF_FEEDBACK_NOT_FOUND));

        reportActivityLogRepository.acquireSourceLock(
                SourceDomain.EVALUATION.name() + ":self-feedback:" + selfFeedback.getId());
        selfFeedback.updateContent(request.content());

        reportActivityLogRepository.refreshSourceSnapshot(
                SourceDomain.EVALUATION.name(), "self-feedback:" + selfFeedback.getId(),
                selfFeedback.getContent(), "{\"selfFeedbackId\":" + selfFeedback.getId() + "}");
        eventPublisher.publishEvent(new SelfFeedbackSubmittedEvent(
                selfFeedback.getId(), projectMember.getId(),
                selfFeedback.getCreatedAt() != null ? selfFeedback.getCreatedAt() : TimeUtil.nowUtc()));

        return new SelfFeedbackUpdateResponse(selfFeedback.getId());
    }
}
