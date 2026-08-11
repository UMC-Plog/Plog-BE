package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.dto.request.PeerEvaluationCreateRequest;
import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.dto.response.TargetMemberDto;
import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.integration.service.IntegrationActorMappingStatusService;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
<<<<<<< Updated upstream
import com.plog.domain.project.service.ProjectAccessService;
=======
import com.plog.domain.report.repository.ReportActivityLogRepository;
>>>>>>> Stashed changes
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.global.api.error.EvaluationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private PeerEvaluationRepository peerEvaluationRepository;

    @Mock
    private SelfFeedbackRepository selfFeedbackRepository;

    @Mock
    private IntegrationActorMappingStatusService actorMappingStatusService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ReportActivityLogRepository reportActivityLogRepository;

    private EvaluationService evaluationService;
    private EvaluationParticipantResolver participantResolver;

    @BeforeEach
    void setUp() {
        participantResolver = new EvaluationParticipantResolver(
                projectMemberRepository,
                new ProjectAccessService(projectMemberRepository));
        evaluationService = new EvaluationService(
                projectRepository,
                projectMemberRepository,
                peerEvaluationRepository,
                selfFeedbackRepository,
                actorMappingStatusService,
                participantResolver,
                reportActivityLogRepository,
                eventPublisher
        );
    }

    @Test
    void updatesExistingPeerEvaluationBeforeReportPublication() {
        Project project = project(ProjectStatus.IN_PROGRESS);
        ProjectMember evaluator = activeMember(10L, project);
        ProjectMember evaluatee = activeMember(20L, project);
        PeerEvaluation evaluation = PeerEvaluation.builder()
                .id(105L)
                .evaluator(evaluator)
                .evaluatee(evaluatee)
                .collaborationScore(1)
                .initiativeScore(1)
                .communicationScore(1)
                .outputScore(1)
                .keywords(List.of("기존 키워드"))
                .feedback("기존 평가")
                .build();
        PeerEvaluationCreateRequest request = new PeerEvaluationCreateRequest(
                4, 4, 5, 4, List.of("소통능력"), "수정된 동료 평가");
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(evaluator));
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));
        when(peerEvaluationRepository.findByEvaluatorIdAndEvaluateeId(10L, 20L)).thenReturn(Optional.of(evaluation));

        var response = evaluationService.updatePeerEvaluation(1L, 20L, 7L, request);

        assertThat(response.peerId()).isEqualTo(105L);
        assertThat(response.isNudgeTriggered()).isFalse();
        assertThat(evaluation.getCollaborationScore()).isEqualTo(4);
        assertThat(evaluation.getKeywords()).containsExactly("소통능력");
        assertThat(evaluation.getFeedback()).isEqualTo("수정된 동료 평가");
        verify(reportActivityLogRepository).refreshSourceSnapshot(
                "EVALUATION", "peer-evaluation:105", "수정된 동료 평가",
                "{\"evaluationId\":105,\"evaluatorId\":10,\"evaluateeId\":20,"
                        + "\"collaborationScore\":4,\"initiativeScore\":4,"
                        + "\"communicationScore\":5,\"outputScore\":4}");
    }

    @Test
    void rejectsUpdateAfterReportPublication() {
        Project project = project(ProjectStatus.COMPLETED);
        ProjectMember evaluator = activeMember(10L, project);
        ProjectMember evaluatee = activeMember(20L, project);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(evaluator));
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        assertThatThrownBy(() -> evaluationService.updatePeerEvaluation(
                1L, 20L, 7L, new PeerEvaluationCreateRequest(4, 4, 5, 4, List.of("소통능력"), "수정된 동료 평가")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.CANNOT_MODIFY_EVALUATION_AFTER_PUBLISH));
    }

    @Test
    void createsAPeerEvaluationOnceTheEndDayHasPassed() {
        Project project = project(ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        ProjectMember evaluator = activeMember(10L, project);
        ProjectMember evaluatee = activeMember(20L, project);
        PeerEvaluationCreateRequest request = new PeerEvaluationCreateRequest(
                4, 4, 5, 4, List.of("소통능력"), "동료 평가");
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(evaluator));
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));
        when(peerEvaluationRepository.findByEvaluatorIdAndEvaluateeId(10L, 20L)).thenReturn(Optional.empty());

        evaluationService.createPeerEvaluation(1L, 20L, 7L, request);

        verify(peerEvaluationRepository).save(any(PeerEvaluation.class));
    }

    @Test
    void rejectsAPeerEvaluationBeforeTheEndDay() {
        Project project = project(ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC).plusDays(1));
        ProjectMember evaluator = activeMember(10L, project);
        ProjectMember evaluatee = activeMember(20L, project);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(evaluator));
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        assertThatThrownBy(() -> evaluationService.createPeerEvaluation(
                1L, 20L, 7L, new PeerEvaluationCreateRequest(4, 4, 5, 4, List.of("소통능력"), "동료 평가")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(EvaluationErrorCode.NOT_EVALUATING_STATE));
    }

    @Test
    void exposesEachTargetsProfilePresetForTheAvatarList() {
        Project project = project(ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC));
        ProjectMember currentMember = activeMember(10L, project);
        ProjectMember teammate = ProjectMember.builder()
                .id(20L)
                .project(project)
                .user(user("바나", ProfilePreset.OTTER))
                .build();
        ProjectMember evaluatedTeammate = ProjectMember.builder()
                .id(30L)
                .project(project)
                .anNickname("프로젝트별명")
                .user(user("개발자", ProfilePreset.PANDA))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(currentMember));
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(1L, MemberStatus.ACTIVE))
                .thenReturn(List.of(currentMember, teammate, evaluatedTeammate));
        when(peerEvaluationRepository.findEvaluatedTargetIds(currentMember)).thenReturn(Set.of(30L));
        when(selfFeedbackRepository.findByProjectMemberId(10L)).thenReturn(Optional.empty());
        when(actorMappingStatusService.isMyMappingCompleted(1L, 10L)).thenReturn(true);

        EvaluationTargetResponse response = evaluationService.getEvaluationTargets(1L, 7L);

        assertThat(response.targets()).containsExactly(
                new TargetMemberDto(20L, "바나", ProfilePreset.OTTER, false),
                new TargetMemberDto(30L, "프로젝트별명", ProfilePreset.PANDA, true)
        );
        assertThat(response.completedPeerEvaluationCount()).isEqualTo(1);
        assertThat(response.totalPeerEvaluationCount()).isEqualTo(2);
        assertThat(response.isSelfFeedbackCompleted()).isFalse();
        assertThat(response.isAccountMappingCompleted()).isTrue();
        assertThat(response.isFinalSubmissionAvailable()).isFalse();
    }

    @Test
    void exposesFinalSubmissionAvailabilityWhenEveryRequirementIsCompleted() {
        Project project = project(ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC));
        ProjectMember currentMember = activeMember(10L, project);
        ProjectMember teammate = ProjectMember.builder()
                .id(20L)
                .project(project)
                .user(user("바나", ProfilePreset.OTTER))
                .build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(currentMember));
        when(projectMemberRepository.findAllByProjectIdAndStatusOrderByIdAsc(1L, MemberStatus.ACTIVE))
                .thenReturn(List.of(currentMember, teammate));
        when(peerEvaluationRepository.findEvaluatedTargetIds(currentMember)).thenReturn(Set.of(20L));
        when(selfFeedbackRepository.findByProjectMemberId(10L))
                .thenReturn(Optional.of(mock(com.plog.domain.evaluation.entity.SelfFeedback.class)));
        when(actorMappingStatusService.isMyMappingCompleted(1L, 10L)).thenReturn(true);

        EvaluationTargetResponse response = evaluationService.getEvaluationTargets(1L, 7L);

        assertThat(response.completedPeerEvaluationCount()).isEqualTo(1);
        assertThat(response.totalPeerEvaluationCount()).isEqualTo(1);
        assertThat(response.isSelfFeedbackCompleted()).isTrue();
        assertThat(response.isAccountMappingCompleted()).isTrue();
        assertThat(response.isFinalSubmissionAvailable()).isTrue();
    }

    @Test
    void rejectsEvaluationAccessForExitedMember() {
        Project project = project(ProjectStatus.IN_PROGRESS, LocalDate.now(ZoneOffset.UTC));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.getEvaluationTargets(1L, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));
    }

    private User user(String nickname, ProfilePreset profilePreset) {
        User user = mock(User.class);
        lenient().when(user.getNickname()).thenReturn(nickname);
        lenient().when(user.getProfilePreset()).thenReturn(profilePreset);
        return user;
    }

    private ProjectMember activeMember(Long id, Project project) {
        return ProjectMember.builder()
                .id(id)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
    }

    private Project project(ProjectStatus status) {
        return project(status, LocalDate.of(2026, 12, 31));
    }

    private Project project(ProjectStatus status, LocalDate endDay) {
        return Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("invite-token-hash")
                .inviteTokenEncrypted("invite-token-encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(status)
                .startDay(LocalDate.of(2026, 1, 1))
                .endDay(endDay)
                .build();
    }
}
