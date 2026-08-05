package com.plog.domain.report.service;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvaluationActivityLogService {
    private final ReportActivityLogRepository activityLogRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectPeerEvaluation(Long evaluationId, LocalDateTime occurredAt) {
        PeerEvaluation evaluation = peerEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalStateException("Peer 평가를 찾을 수 없습니다."));
        String sourceRefId = "peer-evaluation:" + evaluationId;
        saveIfAbsent(
                sourceRefId,
                evaluation.getEvaluator(),
                RawActivityType.PEER_EVALUATION_SUBMIT,
                evaluation.getFeedback(),
                occurredAt,
                "{\"evaluationId\":" + evaluationId
                        + ",\"evaluatorId\":" + evaluation.getEvaluator().getId()
                        + ",\"evaluateeId\":" + evaluation.getEvaluatee().getId()
                        + ",\"collaborationScore\":" + evaluation.getCollaborationScore()
                        + ",\"initiativeScore\":" + evaluation.getInitiativeScore()
                        + ",\"communicationScore\":" + evaluation.getCommunicationScore()
                        + ",\"outputScore\":" + evaluation.getOutputScore() + "}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectSelfFeedback(Long selfFeedbackId, LocalDateTime occurredAt) {
        SelfFeedback feedback = selfFeedbackRepository.findById(selfFeedbackId)
                .orElseThrow(() -> new IllegalStateException("자기 피드백을 찾을 수 없습니다."));
        saveIfAbsent(
                "self-feedback:" + selfFeedbackId,
                feedback.getProjectMember(),
                RawActivityType.SELF_FEEDBACK_SUBMIT,
                feedback.getContent(),
                occurredAt,
                "{\"selfFeedbackId\":" + selfFeedbackId + "}");
    }

    private void saveIfAbsent(
            String sourceRefId,
            ProjectMember member,
            RawActivityType activityType,
            String content,
            LocalDateTime occurredAt,
            String metadata
    ) {
        String sourceKey = SourceDomain.EVALUATION.name() + ":" + sourceRefId;
        activityLogRepository.acquireSourceLock(sourceKey);
        if (activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.EVALUATION, sourceRefId)) {
            return;
        }
        activityLogRepository.save(ReportActivityLog.create(
                member, SourceDomain.EVALUATION, activityType, content, occurredAt, metadata, sourceRefId));
    }
}
