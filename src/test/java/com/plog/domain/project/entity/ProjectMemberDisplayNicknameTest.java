package com.plog.domain.project.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.user.entity.User;
import org.junit.jupiter.api.Test;

class ProjectMemberDisplayNicknameTest {

    @Test
    void 프로젝트_별칭이_공백이면_사용자_닉네임을_사용한다() {
        User user = User.createLocal("issue224@example.com", "encoded", "이름", "기본닉네임");
        ProjectMember member = ProjectMember.builder().user(user).anNickname("   ").build();

        assertThat(member.getDisplayNickname()).isEqualTo("기본닉네임");
    }

    @Test
    void 프로젝트_별칭이_있으면_우선한다() {
        User user = User.createLocal("issue224-2@example.com", "encoded", "이름", "기본닉네임");
        ProjectMember member = ProjectMember.builder().user(user).anNickname("프로젝트닉네임").build();

        assertThat(member.getDisplayNickname()).isEqualTo("프로젝트닉네임");
    }
}
