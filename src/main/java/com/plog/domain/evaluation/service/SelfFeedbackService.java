package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.dto.response.SelfFeedbackResponse;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelfFeedbackService {

    private final ProjectMemberRepository projectMemberRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;

    public SelfFeedbackResponse getMySelfFeedback(Long projectId, Long userId) {

        ProjectMember projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));


        SelfFeedback selfFeedback = selfFeedbackRepository.findByProjectMemberId(projectMember.getId())
                .orElseThrow(() -> new ApiException(EvaluationErrorCode.SELF_FEEDBACK_NOT_FOUND));


        return new SelfFeedbackResponse(selfFeedback.getId(), selfFeedback.getContent());
    }
}