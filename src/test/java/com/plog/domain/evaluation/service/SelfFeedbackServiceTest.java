package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.dto.request.SelfFeedbackCreateRequest;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SelfFeedbackServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private SelfFeedbackRepository selfFeedbackRepository;

    private SelfFeedbackService selfFeedbackService;

    @BeforeEach
    void setUp() {
        selfFeedbackService = new SelfFeedbackService(
                selfFeedbackRepository,
                new EvaluationParticipantResolver(projectMemberRepository)
        );
    }

    @Test
    void updatesExistingSelfFeedbackBeforeReportPublication() {
        ProjectMember projectMember = projectMember(ProjectStatus.IN_PROGRESS);
        SelfFeedback selfFeedback = SelfFeedback.builder()
                .id(12L)
                .projectMember(projectMember)
                .content("기존 피드백")
                .build();
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 7L)).thenReturn(Optional.of(projectMember));
        when(selfFeedbackRepository.findByProjectMemberId(10L)).thenReturn(Optional.of(selfFeedback));

        var response = selfFeedbackService.updateSelfFeedback(1L, 7L, new SelfFeedbackCreateRequest("수정된 피드백"));

        assertThat(response.selfFeedbackId()).isEqualTo(12L);
        assertThat(selfFeedback.getContent()).isEqualTo("수정된 피드백");
    }

    @Test
    void rejectsUpdateAfterReportPublication() {
        ProjectMember projectMember = projectMember(ProjectStatus.COMPLETED);
        when(projectMemberRepository.findByProjectIdAndUserId(1L, 7L)).thenReturn(Optional.of(projectMember));

        assertThatThrownBy(() -> selfFeedbackService.updateSelfFeedback(
                1L, 7L, new SelfFeedbackCreateRequest("수정된 피드백")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.CANNOT_MODIFY_FEEDBACK_AFTER_PUBLISH));
    }

    private ProjectMember projectMember(ProjectStatus status) {
        Project project = Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(status)
                .startDay(LocalDate.of(2026, 1, 1))
                .endDay(LocalDate.of(2026, 12, 31))
                .build();
        return ProjectMember.builder()
                .id(10L)
                .project(project)
                .build();
    }
}
