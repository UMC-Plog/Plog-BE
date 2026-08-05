package com.plog.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.projection.EvaluationLogRecoveryTarget;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 안전망 재수집 anti-join 쿼리 검증. AFTER_COMMIT 리스너가 유실한 제출(아직 ReportActivityLog가
 * 없는 행)만 정확히 골라내는지, 이미 로그가 있는 제출은 제외하는지 확인한다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportActivityLogRecoveryQueryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ReportActivityLogRepository activityLogRepository;
    @Autowired
    private PeerEvaluationRepository peerEvaluationRepository;
    @Autowired
    private SelfFeedbackRepository selfFeedbackRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void findsPeerEvaluationsWithoutActivityLogAndExcludesLoggedOnes() {
        Project project = saveProject("peer");
        ProjectMember evaluator = saveMember(project, "evaluator", ProjectRole.OWNER);
        ProjectMember evaluatee = saveMember(project, "evaluatee", ProjectRole.MEMBER);

        PeerEvaluation logged = savePeerEvaluation(evaluator, evaluatee);
        PeerEvaluation missing = savePeerEvaluation(evaluatee, evaluator);
        activityLogRepository.save(ReportActivityLog.create(
                evaluator, SourceDomain.EVALUATION, RawActivityType.PEER_EVALUATION_SUBMIT,
                "feedback", LocalDateTime.now(ZoneOffset.UTC), "{}",
                "peer-evaluation:" + logged.getId()));

        List<EvaluationLogRecoveryTarget> targets = activityLogRepository
                .findPeerEvaluationsMissingActivityLog(future(), Limit.of(200));

        assertThat(targets).extracting(EvaluationLogRecoveryTarget::getId)
                .containsExactly(missing.getId());
        assertThat(targets.get(0).getOccurredAt()).isNotNull();
    }

    @Test
    void findsSelfFeedbacksWithoutActivityLogAndExcludesLoggedOnes() {
        Project project = saveProject("self");
        ProjectMember loggedMember = saveMember(project, "logged", ProjectRole.OWNER);
        ProjectMember missingMember = saveMember(project, "missing", ProjectRole.MEMBER);

        SelfFeedback logged = saveSelfFeedback(loggedMember);
        SelfFeedback missing = saveSelfFeedback(missingMember);
        activityLogRepository.save(ReportActivityLog.create(
                loggedMember, SourceDomain.EVALUATION, RawActivityType.SELF_FEEDBACK_SUBMIT,
                "content", LocalDateTime.now(ZoneOffset.UTC), "{}",
                "self-feedback:" + logged.getId()));

        List<EvaluationLogRecoveryTarget> targets = activityLogRepository
                .findSelfFeedbacksMissingActivityLog(future(), Limit.of(200));

        assertThat(targets).extracting(EvaluationLogRecoveryTarget::getId)
                .containsExactly(missing.getId());
    }

    private LocalDateTime future() {
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1);
    }

    private PeerEvaluation savePeerEvaluation(ProjectMember evaluator, ProjectMember evaluatee) {
        return peerEvaluationRepository.save(PeerEvaluation.builder()
                .evaluator(evaluator)
                .evaluatee(evaluatee)
                .collaborationScore(4)
                .initiativeScore(4)
                .communicationScore(4)
                .outputScore(4)
                .feedback("nice")
                .build());
    }

    private SelfFeedback saveSelfFeedback(ProjectMember member) {
        return selfFeedbackRepository.save(SelfFeedback.builder()
                .projectMember(member)
                .content("retrospective")
                .build());
    }

    private ProjectMember saveMember(Project project, String suffix, ProjectRole role) {
        User user = userRepository.save(User.createLocal(
                suffix + "@plog.test", "encoded-password", "User " + suffix, "nickname-" + suffix));
        return projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(role)
                .status(MemberStatus.ACTIVE)
                .build());
    }

    private Project saveProject(String suffix) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return projectRepository.save(Project.builder()
                .projectName("Project " + suffix)
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted-" + suffix)
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build());
    }
}
