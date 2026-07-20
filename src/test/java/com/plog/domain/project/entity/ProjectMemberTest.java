package com.plog.domain.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectMemberTest {

    @Test
    void reactivatesAnExitedMembershipAsAnActiveMember() {
        ProjectMember projectMember = ProjectMember.builder()
                .role(ProjectRole.OWNER)
                .status(MemberStatus.EXIT)
                .build();

        projectMember.reactivateAsMember();

        assertThat(projectMember.getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(projectMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
}
