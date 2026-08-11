package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.user.entity.User;
import org.junit.jupiter.api.Test;

class ReportMemberResultTest {

    private final Project project = Project.builder()
            .projectName("Plog")
            .inviteTokenHash("invite-hash")
            .inviteTokenEncrypted("encrypted-invite")
            .build();

    private final ProjectMember member = ProjectMember.builder()
            .project(project)
            .user(User.createLocal("min@plog.test", "encoded", "송민", "송민"))
            .role(ProjectRole.MEMBER)
            .status(MemberStatus.ACTIVE)
            .build();

    @Test
    void 생성_직후에는_업무_집계가_0이다() {
        Report report = Report.start(project);

        ReportMemberResult result = ReportMemberResult.create(report, member);

        assertThat(result.getTotalTaskCount()).isZero();
        assertThat(result.getCompletedTaskCount()).isZero();
        assertThat(result.getDeadlineMetTaskCount()).isZero();
    }

    @Test
    void 업무_집계를_적용하면_세_값이_모두_반영된다() {
        Report report = Report.start(project);
        ReportMemberResult result = ReportMemberResult.create(report, member);

        result.applyTaskCounts(13, 12, 11);

        assertThat(result.getTotalTaskCount()).isEqualTo(13);
        assertThat(result.getCompletedTaskCount()).isEqualTo(12);
        assertThat(result.getDeadlineMetTaskCount()).isEqualTo(11);
    }

    @Test
    void 개인과_팀원_headline이_모두_있어야_LLM_완료로_판정한다() {
        ReportMemberResult result = ReportMemberResult.create(Report.start(project), member);

        result.applyLlmText(new ReportMemberResult.LlmTextPayload(
                "개인 headline", null, null, null, null, null, "{}", "stub"));
        assertThat(result.hasLlmText()).isFalse();

        result.applyLlmText(new ReportMemberResult.LlmTextPayload(
                "개인 headline", "팀원 headline", null, null, null, null, "{}", "stub"));
        assertThat(result.hasLlmText()).isTrue();
    }
}
