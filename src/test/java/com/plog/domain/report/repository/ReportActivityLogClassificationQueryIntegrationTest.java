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
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
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
 * 2단계(활동 유형 분류) 배치 대상 조회 쿼리 검증. "정제 통과(noiseFiltered=false) +
 * 임베딩 완료(embeddingModel IS NOT NULL) + 미분류(classifiedType IS NULL) + TASK/CHAT/POST
 * 도메인"이라는 4개 조건이 실제 DB 레벨에서 정확히 걸리는지 확인한다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportActivityLogClassificationQueryIntegrationTest {

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
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private UserRepository userRepository;

    private static final List<SourceDomain> CLASSIFIABLE_DOMAINS =
            List.of(SourceDomain.TASK, SourceDomain.CHAT, SourceDomain.POST);

    @Test
    void 정제_통과_임베딩_완료_미분류_행만_대상으로_조회한다() {
        Project project = saveProject();
        ProjectMember member = saveMember(project);

        // 대상: 정제 통과 + 임베딩 완료 + 미분류
        ReportActivityLog target = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "분류 대상 메시지", "gemini-embedding-001", "[0.1,0.2]", false, null);

        // 제외: 아직 임베딩 전(embeddingModel null)
        save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "임베딩 전 메시지", null, null, false, null);

        // 제외: 이미 분류됨
        save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "이미 분류된 메시지", "gemini-embedding-001", "[0.3,0.4]", false, ActivityCategory.DECISION);

        // 제외: 아직 정제 전(noiseFiltered null)
        ReportActivityLog notRefined = ReportActivityLog.create(
                member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, "정제 전 메시지",
                LocalDateTime.now(ZoneOffset.UTC), null, null);
        activityLogRepository.save(notRefined);

        // 제외: 분류 대상 도메인이 아님(EVALUATION)
        ReportActivityLog evaluationLog = ReportActivityLog.create(
                member, SourceDomain.EVALUATION, RawActivityType.SELF_FEEDBACK_SUBMIT, "자기 피드백",
                LocalDateTime.now(ZoneOffset.UTC), null, "self-feedback:1");
        activityLogRepository.save(evaluationLog);

        List<ReportActivityLog> result = activityLogRepository
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        CLASSIFIABLE_DOMAINS, Limit.of(500));

        assertThat(result).extracting(ReportActivityLog::getId).containsExactly(target.getId());
    }

    @Test
    void content가_없어_N_A로_찍힌_행도_대상에_포함된다() {
        Project project = saveProject();
        ProjectMember member = saveMember(project);

        ReportActivityLog statusChange = ReportActivityLog.create(
                member, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE, null,
                LocalDateTime.now(ZoneOffset.UTC), "{\"newStatus\":\"DONE\"}", null);
        activityLogRepository.save(statusChange);
        statusChange.applyNoiseFilter(false);
        statusChange.markEmbeddingNotApplicable();

        List<ReportActivityLog> result = activityLogRepository
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        CLASSIFIABLE_DOMAINS, Limit.of(500));

        assertThat(result).extracting(ReportActivityLog::getId).containsExactly(statusChange.getId());
    }

    @Test
    void occurredAt_오름차순으로_정렬하고_동시각이면_id_오름차순으로_정렬한다() {
        Project project = saveProject();
        ProjectMember member = saveMember(project);
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 1, 9, 0);

        // 서로 다른 occurredAt: 가장 늦은 시각을 먼저 저장해서 저장 순서와 occurredAt 순서가
        // 다르다는 것을 명확히 한다 — occurredAt 기준으로 정렬되는지가 핵심.
        ReportActivityLog latest = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "가장 늦은 시각", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime.plusMinutes(20));
        ReportActivityLog earliest = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "가장 이른 시각", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime);

        // 같은 occurredAt(baseTime + 10분)을 가진 두 행 — id가 tie-breaker가 되는지 확인.
        // save()가 activityLogRepository.save()를 먼저 호출하므로 아래 호출 순서가 곧 id 순서다.
        ReportActivityLog sameTimeFirstSaved = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "동시각 먼저 저장", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime.plusMinutes(10));
        ReportActivityLog sameTimeSecondSaved = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "동시각 나중 저장", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime.plusMinutes(10));

        List<ReportActivityLog> result = activityLogRepository
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        CLASSIFIABLE_DOMAINS, Limit.of(500));

        assertThat(result).extracting(ReportActivityLog::getId).containsExactly(
                earliest.getId(),
                sameTimeFirstSaved.getId(),
                sameTimeSecondSaved.getId(),
                latest.getId()
        );
    }

    @Test
    void Limit을_넘는_행이_있어도_지정한_개수만큼만_정렬된_앞부분을_반환한다() {
        Project project = saveProject();
        ProjectMember member = saveMember(project);
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 1, 9, 0);

        ReportActivityLog first = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "첫번째", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime);
        ReportActivityLog second = save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "두번째", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime.plusMinutes(1));
        // 세 번째 행은 대상이지만 Limit(2)에 걸려 결과에 포함되면 안 된다.
        save(member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "세번째(제한에 걸려 제외)", "gemini-embedding-001", "[0.1,0.2]", false, null, baseTime.plusMinutes(2));

        List<ReportActivityLog> result = activityLogRepository
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        CLASSIFIABLE_DOMAINS, Limit.of(2));

        assertThat(result).extracting(ReportActivityLog::getId).containsExactly(first.getId(), second.getId());
    }

    private ReportActivityLog save(
            ProjectMember member, SourceDomain domain, RawActivityType rawType, String content,
            String embeddingModel, String embeddingJson, boolean noiseFiltered, ActivityCategory classifiedType
    ) {
        return save(member, domain, rawType, content, embeddingModel, embeddingJson, noiseFiltered,
                classifiedType, LocalDateTime.now(ZoneOffset.UTC));
    }

    private ReportActivityLog save(
            ProjectMember member, SourceDomain domain, RawActivityType rawType, String content,
            String embeddingModel, String embeddingJson, boolean noiseFiltered, ActivityCategory classifiedType,
            LocalDateTime occurredAt
    ) {
        ReportActivityLog log = ReportActivityLog.create(
                member, domain, rawType, content, occurredAt, null, null);
        activityLogRepository.save(log);
        log.applyNoiseFilter(noiseFiltered);
        if (embeddingModel != null) {
            log.applyEmbedding(embeddingModel, embeddingJson);
        }
        if (classifiedType != null) {
            log.classify(classifiedType);
        }
        return log;
    }

    private ProjectMember saveMember(Project project) {
        User user = userRepository.save(User.createLocal(
                "member-" + UUID.randomUUID() + "@plog.test", "encoded-password", "User", "nickname"));
        return projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build());
    }

    private Project saveProject() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return projectRepository.save(Project.builder()
                .projectName("Project " + UUID.randomUUID())
                .inviteTokenHash(UUID.randomUUID().toString())
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(today)
                .endDay(today.plusDays(30))
                .build());
    }
}