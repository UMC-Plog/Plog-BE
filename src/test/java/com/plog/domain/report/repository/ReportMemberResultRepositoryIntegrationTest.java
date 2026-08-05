package com.plog.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.projection.ReportMemberSummary;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportMemberResultRepositoryIntegrationTest {

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
    private ReportMemberResultRepository memberResultRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 표시 닉네임 규칙(anNickname 우선, 공백이면 user.nickname)과 정렬(점수 내림차순, 미계산은 뒤)을
     * 실제 SQL 로 확인한다. coalesce/nullif/trim 과 case 정렬은 단위 테스트로는 검증되지 않는다.
     */
    @Test
    void projectsMemberSummariesWithDisplayNicknameAndScoreOrdering() {
        Project project = saveProject("Plog");
        Report report = reportRepository.save(Report.start(project));
        ProjectMember withAlias = saveMember(project, "chang", "창훈", "별명창훈");
        ProjectMember blankAlias = saveMember(project, "somin", "송민", "   ");
        ProjectMember unscored = saveMember(project, "hyunmo", "현모", null);
        saveResult(report, withAlias, new BigDecimal("70.00"));
        saveResult(report, blankAlias, new BigDecimal("82.50"));
        saveResult(report, unscored, null);
        entityManager.flush();
        entityManager.clear();

        List<ReportMemberSummary> summaries = memberResultRepository.findMemberSummaries(report.getId());

        assertThat(summaries).extracting(ReportMemberSummary::getMemberName)
                .containsExactly("송민", "별명창훈", "현모");
        assertThat(summaries.getFirst().getFinalScore()).isEqualByComparingTo("82.50");
        assertThat(summaries.getFirst().getReliabilityTier()).isEqualTo(ReliabilityTier.P1);
        assertThat(summaries.getLast().getFinalScore()).isNull();
    }

    @Test
    void loadsMemberAndUserInOneQueryForTheResultDetail() {
        Project project = saveProject("Plog");
        Report report = reportRepository.save(Report.start(project));
        ProjectMember member = saveMember(project, "chang", "창훈", null);
        saveResult(report, member, new BigDecimal("82.50"));
        entityManager.flush();
        entityManager.clear();

        Optional<ReportMemberResult> found = memberResultRepository
                .findWithMemberByReportIdAndProjectMemberId(report.getId(), member.getId());

        assertThat(found).isPresent();
        // 영속성 컨텍스트를 비운 뒤라 fetch 가 안 됐으면 여기서 LazyInitializationException 이 난다.
        entityManager.clear();
        assertThat(found.get().getProjectMember().getDisplayNickname()).isEqualTo("창훈");
    }

    @Test
    void findsNothingForAnotherReportsMember() {
        Project project = saveProject("Plog");
        Report report = reportRepository.save(Report.start(project));
        Report otherReport = reportRepository.save(Report.start(project));
        ProjectMember member = saveMember(project, "chang", "창훈", null);
        saveResult(report, member, new BigDecimal("82.50"));
        entityManager.flush();
        entityManager.clear();

        assertThat(memberResultRepository
                .findWithMemberByReportIdAndProjectMemberId(otherReport.getId(), member.getId()))
                .isEmpty();
        assertThat(memberResultRepository.findMemberSummaries(otherReport.getId())).isEmpty();
    }

    /**
     * 배치 대상 쿼리. 유예 경계와 "리포트 없음" 조건을 실제 SQL 로 확인한다 —
     * not exists 서브쿼리와 날짜 경계는 단위 테스트로 검증되지 않는다.
     */
    @Test
    void findsOnlyProjectsPastTheEvaluationDeadlineWithoutAnActiveReport() {
        LocalDate today = LocalDate.of(2026, 8, 5);
        LocalDate bound = Project.latestEndDayWithClosedEvaluation(today);
        Project due = saveProjectEndingOn("마감지남", bound.minusDays(1));
        Project exactlyDue = saveProjectEndingOn("마감당일", bound);
        Project notYet = saveProjectEndingOn("유예중", bound.plusDays(1));
        Project alreadyReported = saveProjectEndingOn("리포트있음", bound.minusDays(1));
        reportRepository.save(Report.start(alreadyReported));
        Project failedOnly = saveProjectEndingOn("실패만있음", bound.minusDays(1));
        Report failed = Report.start(failedOnly);
        failed.fail();
        reportRepository.save(failed);
        entityManager.flush();
        entityManager.clear();

        List<Project> found = reportRepository.findProjectsDueForReport(
                bound, ReportStatus.restartBlockingStatuses(), PageRequest.of(0, 100));

        assertThat(found).extracting(Project::getId)
                .containsExactlyInAnyOrder(due.getId(), exactlyDue.getId(), failedOnly.getId())
                .doesNotContain(notYet.getId(), alreadyReported.getId());
    }

    @Test
    void ordersDueProjectsByOldestDeadlineSoBatchSizeCutIsStable() {
        LocalDate bound = Project.latestEndDayWithClosedEvaluation(LocalDate.of(2026, 8, 5));
        Project newer = saveProjectEndingOn("최근", bound.minusDays(1));
        Project older = saveProjectEndingOn("오래됨", bound.minusDays(30));
        entityManager.flush();
        entityManager.clear();

        List<Project> found = reportRepository.findProjectsDueForReport(
                bound, ReportStatus.restartBlockingStatuses(), PageRequest.of(0, 1));

        assertThat(found).extracting(Project::getId).containsExactly(older.getId());
        assertThat(found).extracting(Project::getId).doesNotContain(newer.getId());
    }

    /** 리포트 생성 멱등성의 근거 쿼리 — FAILED 만 있으면 재시작이 열려야 한다. */
    @Test
    void treatsFailedReportAsRestartable() {
        Project project = saveProject("Plog");
        Report failed = Report.start(project);
        failed.fail();
        reportRepository.save(failed);

        assertThat(reportRepository.existsByProjectIdAndStatusIn(
                project.getId(), ReportStatus.restartBlockingStatuses())).isFalse();

        reportRepository.save(Report.start(project));

        assertThat(reportRepository.existsByProjectIdAndStatusIn(
                project.getId(), ReportStatus.restartBlockingStatuses())).isTrue();
    }

    private ReportMemberResult saveResult(Report report, ProjectMember member, BigDecimal finalScore) {
        ReportMemberResult result = ReportMemberResult.create(report, member);
        result.applyScores(
                new BigDecimal("88.00"),
                null,
                new BigDecimal("80.00"),
                new BigDecimal("70.00"),
                finalScore,
                false,
                ReliabilityTier.P1,
                null
        );
        return memberResultRepository.save(result);
    }

    private ProjectMember saveMember(Project project, String handle, String nickname, String anNickname) {
        User user = userRepository.save(User.createLocal(
                handle + "@plog.test", "encoded", nickname, nickname));
        return projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .anNickname(anNickname)
                .build());
    }

    private Project saveProjectEndingOn(String name, LocalDate endDay) {
        return projectRepository.save(Project.builder()
                .projectName(name)
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted-" + name)
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(endDay.minusDays(30))
                .endDay(endDay)
                .build());
    }

    private Project saveProject(String name) {
        LocalDate today = LocalDate.of(2026, 7, 21);
        return projectRepository.save(Project.builder()
                .projectName(name)
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted-" + name)
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build());
    }
}
