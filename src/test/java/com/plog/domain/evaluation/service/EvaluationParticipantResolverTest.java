package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationParticipantResolverTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Test
    void resolvesActiveEvaluatorThroughProjectAccessPolicy() {
        ProjectMember evaluator = mock(ProjectMember.class);
        when(projectAccessService.requireActiveMember(1L, 7L)).thenReturn(evaluator);
        EvaluationParticipantResolver resolver = resolver();

        assertThat(resolver.requireEvaluator(1L, 7L)).isSameAs(evaluator);
        verify(projectAccessService).requireActiveMember(1L, 7L);
    }

    @Test
    void resolvesEvaluateeBelongingToProject() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(1L);
        ProjectMember evaluatee = ProjectMember.builder()
                .id(20L)
                .project(project)
                .status(MemberStatus.ACTIVE)
                .build();
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        EvaluationParticipantResolver resolver = resolver();

        assertThat(resolver.requireEvaluatee(1L, 20L)).isSameAs(evaluatee);
    }

    @Test
    void rejectsEvaluateeFromAnotherProject() {
        Project anotherProject = mock(Project.class);
        when(anotherProject.getId()).thenReturn(2L);
        ProjectMember evaluatee = ProjectMember.builder()
                .id(20L)
                .project(anotherProject)
                .status(MemberStatus.ACTIVE)
                .build();
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        EvaluationParticipantResolver resolver = resolver();

        assertThatThrownBy(() -> resolver.requireEvaluatee(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsExitedEvaluateeFromTheSameProject() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(1L);
        ProjectMember evaluatee = ProjectMember.builder()
                .id(20L)
                .project(project)
                .status(MemberStatus.EXIT)
                .build();
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));
        EvaluationParticipantResolver resolver = resolver();

        assertThatThrownBy(() -> resolver.requireEvaluatee(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private EvaluationParticipantResolver resolver() {
        return new EvaluationParticipantResolver(projectMemberRepository, projectAccessService);
    }
}
