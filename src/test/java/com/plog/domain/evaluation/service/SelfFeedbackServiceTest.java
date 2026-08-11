package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.dto.request.SelfFeedbackCreateRequest;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.PeerEvaluationStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.event.EvaluationCompletionCheckRequestedEvent;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SelfFeedbackServiceTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private SelfFeedbackRepository selfFeedbackRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ReportActivityLogRepository reportActivityLogRepository;

    private SelfFeedbackService selfFeedbackService;

    @BeforeEach
    void setUp() {
        selfFeedbackService = new SelfFeedbackService(
                selfFeedbackRepository,
                new EvaluationParticipantResolver(
                        projectMemberRepository,
                        new ProjectAccessService(projectMemberRepository)),
                reportActivityLogRepository,
                eventPublisher
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
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(projectMember));
        when(selfFeedbackRepository.findByProjectMemberId(10L)).thenReturn(Optional.of(selfFeedback));

        var response = selfFeedbackService.updateSelfFeedback(1L, 7L, new SelfFeedbackCreateRequest("수정된 피드백"));

        assertThat(response.selfFeedbackId()).isEqualTo(12L);
        assertThat(selfFeedback.getContent()).isEqualTo("수정된 피드백");
        verify(reportActivityLogRepository).refreshSourceSnapshot(
                "EVALUATION", "self-feedback:12", "수정된 피드백", "{\"selfFeedbackId\":12}");
    }

    @Test
    void rejectsUpdateAfterReportPublication() {
        ProjectMember projectMember = projectMember(ProjectStatus.COMPLETED);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(projectMember));

        assertThatThrownBy(() -> selfFeedbackService.updateSelfFeedback(
                1L, 7L, new SelfFeedbackCreateRequest("수정된 피드백")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.CANNOT_MODIFY_FEEDBACK_AFTER_PUBLISH));
    }

    @Test
    void createsSelfFeedbackOnceTheEndDayHasPassed() {
        ProjectMember projectMember = projectMember(
                ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(projectMember));
        when(selfFeedbackRepository.findByProjectMemberId(10L)).thenReturn(Optional.empty());

        selfFeedbackService.createSelfFeedback(1L, 7L, new SelfFeedbackCreateRequest("셀프 피드백"));

        verify(selfFeedbackRepository).saveAndFlush(any(SelfFeedback.class));
        verify(eventPublisher).publishEvent(new EvaluationCompletionCheckRequestedEvent(1L));
    }

    // 리포트 발행 = 셀프 피드백 마감. 리포트가 셀프 피드백을 분석 재료로 쓰게 될 예정이라,
    // 발행 후에 들어온 피드백은 반영할 곳이 없다. 필수가 아닐 뿐 아무 때나 낼 수 있는 것은 아니다.
    @Test
    void rejectsSelfFeedbackAfterReportPublication() {
        ProjectMember projectMember = projectMember(
                ProjectStatus.COMPLETED, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(projectMember));

        assertThatThrownBy(() -> selfFeedbackService.createSelfFeedback(
                1L, 7L, new SelfFeedbackCreateRequest("완료 후에 쓴 셀프 피드백")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.NOT_EVALUATING_STATE));
    }

    @Test
    void rejectsSelfFeedbackBeforeTheEndDay() {
        ProjectMember projectMember = projectMember(
                ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC).plusDays(1),
                PeerEvaluationStatus.PENDING);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(projectMember));

        assertThatThrownBy(() -> selfFeedbackService.createSelfFeedback(
                1L, 7L, new SelfFeedbackCreateRequest("셀프 피드백")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.NOT_EVALUATING_STATE));
    }

    @Test
    void rejectsSelfFeedbackAccessForExitedMember() {
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> selfFeedbackService.getMySelfFeedback(1L, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));
    }

    private ProjectMember projectMember(ProjectStatus status) {
        return projectMember(status, LocalDate.of(2026, 12, 31));
    }

    private ProjectMember projectMember(ProjectStatus status, LocalDate endDay) {
        return projectMember(status, endDay, status == ProjectStatus.COMPLETED
                ? PeerEvaluationStatus.CLOSED : PeerEvaluationStatus.OPEN);
    }

    private ProjectMember projectMember(
            ProjectStatus status, LocalDate endDay, PeerEvaluationStatus evaluationStatus) {
        Project project = Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(status)
                .startDay(LocalDate.of(2026, 1, 1))
                .endDay(endDay)
                .peerEvaluationStatus(evaluationStatus)
                .build();
        return ProjectMember.builder()
                .id(10L)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
    }
}
