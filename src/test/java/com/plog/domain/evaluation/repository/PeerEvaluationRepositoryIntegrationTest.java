package com.plog.domain.evaluation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class PeerEvaluationRepositoryIntegrationTest {

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
    private PeerEvaluationRepository peerEvaluationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 받은 평가가 삽입 순서(제출 시각 오름차순, 동시각이면 id 오름차순)대로 안정적으로 돌아오는지 실제 SQL 로 확인한다.
     * ORDER BY 가 없으면 행 순서가 보장되지 않아 peer_keywords 태그 칩의 최초 등장 순서가 실행마다 뒤바뀔 수 있다 —
     * 단위 테스트(mock)로는 검증되지 않는 부분이라 여기서 고정한다.
     */
    @Test
    void returnsEvaluationsInStableSubmissionOrderForKeywordAggregation() {
        Project project = saveProject("Plog");
        ProjectMember evaluatee = saveMember(project, "target");
        ProjectMember first = saveMember(project, "first");
        ProjectMember second = saveMember(project, "second");
        ProjectMember third = saveMember(project, "third");

        // 삽입 순서: first → second → third. 반환도 이 순서여야 한다.
        savePeerEvaluation(first, evaluatee, List.of("리더십", "책임감"));
        savePeerEvaluation(second, evaluatee, List.of("꼼꼼함"));
        savePeerEvaluation(third, evaluatee, List.of("소통", "리더십"));
        entityManager.flush();
        entityManager.clear();

        List<PeerEvaluation> received =
                peerEvaluationRepository.findAllByEvaluateeIdOrderByCreatedAtAscIdAsc(evaluatee.getId());

        // 평가자 순서가 삽입 순서와 같은지 — 이 순서가 확정돼야 키워드 최초 등장 순서가 안정적이다.
        assertThat(received).extracting(evaluation -> evaluation.getEvaluator().getId())
                .containsExactly(first.getId(), second.getId(), third.getId());
        // 그 결과 키워드는 첫 등장 순서로 ["리더십","책임감","꼼꼼함","소통"] 이 된다.
        assertThat(received).flatExtracting(PeerEvaluation::getKeywords)
                .containsExactly("리더십", "책임감", "꼼꼼함", "소통", "리더십");
    }

    private void savePeerEvaluation(ProjectMember evaluator, ProjectMember evaluatee, List<String> keywords) {
        peerEvaluationRepository.save(PeerEvaluation.builder()
                .evaluator(evaluator)
                .evaluatee(evaluatee)
                .collaborationScore(4)
                .initiativeScore(4)
                .communicationScore(4)
                .outputScore(4)
                .keywords(keywords)
                .feedback("good")
                .build());
    }

    private ProjectMember saveMember(Project project, String handle) {
        User user = userRepository.save(User.createLocal(
                handle + "@plog.test", "encoded", handle, handle));
        return projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
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
