package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.dto.response.EvaluationTargetListResponse;
import com.plog.domain.evaluation.repository.PeerReviewRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectMemberRole;
import com.plog.domain.project.entity.ProjectMemberStatus;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    private static final String PROJECT_ID = "project-1";
    private static final String USER_ID = "user-1";

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private PeerReviewRepository peerReviewRepository;

    @InjectMocks
    private EvaluationService evaluationService;

    @Test
    void getEvaluationTargetsReturnsActiveOtherMembersWithEvaluationStatus() {
        Project project = project(ProjectStatus.EVALUATING);
        ProjectMember currentMember = projectMember("pm-1", USER_ID, "바나나");
        ProjectMember targetMember = projectMember("pm-2", "user-2", "포도");

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserIdAndProjectStatus(
                PROJECT_ID,
                USER_ID,
                ProjectMemberStatus.ACTIVE
        )).thenReturn(Optional.of(currentMember));
        when(projectMemberRepository.findAllByProjectIdAndProjectStatusAndProjectMemberIdNot(
                PROJECT_ID,
                ProjectMemberStatus.ACTIVE,
                "pm-1"
        )).thenReturn(List.of(targetMember));
        when(peerReviewRepository.existsByProjectIdAndEvaluatorIdAndEvaluateeId(
                PROJECT_ID,
                "pm-1",
                "pm-2"
        )).thenReturn(true);

        EvaluationTargetListResponse response = evaluationService.getEvaluationTargets(PROJECT_ID, USER_ID);

        assertThat(response.targets()).hasSize(1);
        assertThat(response.targets().get(0).projectMemberId()).isEqualTo("pm-2");
        assertThat(response.targets().get(0).nickname()).isEqualTo("포도");
        assertThat(response.targets().get(0).isEvaluated()).isTrue();
    }

    @Test
    void getEvaluationTargetsRejectsProjectThatIsNotEvaluationReady() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project(ProjectStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> evaluationService.getEvaluationTargets(PROJECT_ID, USER_ID))
                .isInstanceOf(ApiException.class);
    }

    private Project project(ProjectStatus status) {
        return Project.builder()
                .projectId(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .projectType(ProjectType.DEVELOP)
                .status(status)
                .startDay(LocalDate.now())
                .endDay(LocalDate.now().plusDays(7))
                .build();
    }

    private ProjectMember projectMember(String projectMemberId, String userId, String anNickname) {
        return ProjectMember.builder()
                .projectMemberId(projectMemberId)
                .projectId(PROJECT_ID)
                .userId(userId)
                .role(ProjectMemberRole.MEMBER)
                .projectStatus(ProjectMemberStatus.ACTIVE)
                .anNickname(anNickname)
                .build();
    }
}
