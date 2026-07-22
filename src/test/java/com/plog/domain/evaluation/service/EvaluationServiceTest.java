package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.dto.request.PeerEvaluationCreateRequest;
import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private PeerEvaluationRepository peerEvaluationRepository;

    private EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new EvaluationService(projectRepository, projectMemberRepository, peerEvaluationRepository);
    }

    @Test
    void updatesExistingPeerEvaluationBeforeReportPublication() {
        Project project = project(ProjectStatus.IN_PROGRESS);
        ProjectMember evaluator = ProjectMember.builder().id(10L).project(project).build();
        ProjectMember evaluatee = ProjectMember.builder().id(20L).project(project).build();
        PeerEvaluation evaluation = PeerEvaluation.builder()
                .id(105L)
                .evaluator(evaluator)
                .evaluatee(evaluatee)
                .collaborationScore(1)
                .initiativeScore(1)
                .responsibilityScore(1)
                .communicationScore(1)
                .outputScore(1)
                .keywords(List.of("기존 키워드"))
                .feedback("기존 평가")
                .build();
        PeerEvaluationCreateRequest request = new PeerEvaluationCreateRequest(
                4, 4, 5, 5, 4, List.of("소통능력"), "수정된 동료 평가");
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 7L)).thenReturn(Optional.of(evaluator));
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));
        when(peerEvaluationRepository.findByEvaluatorIdAndEvaluateeId(10L, 20L)).thenReturn(Optional.of(evaluation));

        var response = evaluationService.updatePeerEvaluation(1L, 20L, 7L, request);

        assertThat(response.peerId()).isEqualTo(105L);
        assertThat(response.isNudgeTriggered()).isFalse();
        assertThat(evaluation.getCollaborationScore()).isEqualTo(4);
        assertThat(evaluation.getKeywords()).containsExactly("소통능력");
        assertThat(evaluation.getFeedback()).isEqualTo("수정된 동료 평가");
    }

    @Test
    void rejectsUpdateAfterReportPublication() {
        Project project = project(ProjectStatus.COMPLETED);
        ProjectMember evaluator = ProjectMember.builder().id(10L).project(project).build();
        ProjectMember evaluatee = ProjectMember.builder().id(20L).project(project).build();
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 7L)).thenReturn(Optional.of(evaluator));
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        assertThatThrownBy(() -> evaluationService.updatePeerEvaluation(
                1L, 20L, 7L, new PeerEvaluationCreateRequest(4, 4, 5, 5, 4, List.of("소통능력"), "수정된 동료 평가")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.CANNOT_MODIFY_EVALUATION_AFTER_PUBLISH));
    }

    private Project project(ProjectStatus status) {
        return Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(status)
                .startDay(LocalDate.of(2026, 1, 1))
                .endDay(LocalDate.of(2026, 12, 31))
                .build();
    }
}
