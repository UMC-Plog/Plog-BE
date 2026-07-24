package com.plog.domain.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
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

    @Test
    void resolvesEvaluateeBelongingToProject() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(1L);
        ProjectMember evaluatee = ProjectMember.builder().id(20L).project(project).build();
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        EvaluationParticipantResolver resolver = new EvaluationParticipantResolver(projectMemberRepository);

        assertThat(resolver.requireEvaluatee(1L, 20L)).isSameAs(evaluatee);
    }

    @Test
    void rejectsEvaluateeFromAnotherProject() {
        Project anotherProject = mock(Project.class);
        when(anotherProject.getId()).thenReturn(2L);
        ProjectMember evaluatee = ProjectMember.builder().id(20L).project(anotherProject).build();
        when(projectMemberRepository.findById(20L)).thenReturn(Optional.of(evaluatee));

        EvaluationParticipantResolver resolver = new EvaluationParticipantResolver(projectMemberRepository);

        assertThatThrownBy(() -> resolver.requireEvaluatee(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
