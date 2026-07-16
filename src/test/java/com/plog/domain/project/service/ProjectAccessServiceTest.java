package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock
    ProjectMemberRepository projectMemberRepository;

    @Test
    void returnsActiveMember() {
        ProjectMember member = ProjectMember.builder().id(7L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 2L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        ProjectAccessService service = new ProjectAccessService(projectMemberRepository);

        assertThat(service.requireActiveMember(1L, 2L)).isSameAs(member);
    }

    @Test
    void rejectsNonOwner() {
        ProjectMember member = ProjectMember.builder().id(7L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 2L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        ProjectAccessService service = new ProjectAccessService(projectMemberRepository);

        assertThatThrownBy(() -> service.requireOwner(1L, 2L)).isInstanceOf(ApiException.class);
    }
}
