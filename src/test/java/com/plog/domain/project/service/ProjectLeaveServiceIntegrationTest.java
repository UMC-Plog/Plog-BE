package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.plog.domain.project.dto.response.ProjectLeaveResponse;
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
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.ThumbnailProperties;
import com.plog.infrastructure.s3.UploadedFileService;
import com.plog.global.util.HashUtil;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 마지막 멤버 퇴장 시 커밋 flush까지 실제로 태워 회귀를 막는다.
 * <p>
 * 과거 버그: purge()가 project_members를 벌크 delete로 지우면 그 엔티티는 영속성 컨텍스트에 managed로 남는데,
 * 이어서 projectRepository.delete(project)가 project를 REMOVED로 만들면 flush 때 managed member가
 * REMOVED project를 참조해 TransientObjectException(500)이 났다. 목 기반 단위 테스트는 flush를 태우지 않아
 * 이 결함을 잡지 못했으므로, 실제 트랜잭션 커밋이 필요한 이 테스트로 방어한다.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "plog.invite.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProjectLeaveService.class, ProjectPurgeService.class, UploadedFileService.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectLeaveServiceIntegrationTest {

    private static final String INVITE_CODE = "project-leave-invite-code";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // 첨부가 없는 퇴장 경로에서는 호출되지 않지만, UploadedFileService 빈을 구성하려면 필요하다.
    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private ThumbnailProperties thumbnailProperties;

    @Autowired
    private ProjectLeaveService projectLeaveService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            projectMemberRepository.deleteAll();
            projectMemberRepository.flush();
            projectRepository.deleteAll();
            projectRepository.flush();
            userRepository.deleteAll();
            userRepository.flush();
        });
    }

    @Test
    void lastMemberLeaveHardDeletesProjectWithoutFlushError() {
        Fixture fixture = saveOwner("last-member");

        // leave()가 자체 트랜잭션을 열고 커밋한다 → 이 커밋의 flush에서 과거 버그가 터졌다.
        assertThatCode(() -> projectLeaveService.leave(fixture.projectId(), fixture.userId()))
                .doesNotThrowAnyException();

        // 프로젝트와 멤버 행이 모두 사라져야 한다(하드 삭제).
        assertThat(projectRepository.findById(fixture.projectId())).isEmpty();
        assertThat(projectMemberRepository.count()).isZero();
    }

    @Test
    void lastMemberLeaveHardDeletesProjectEvenWhenRoleIsPlainMember() {
        Fixture fixture = saveLoneMember("last-plain-member");

        // 마지막 멤버가 MEMBER 역할이어도 역할과 무관하게 프로젝트를 하드 삭제한다.
        assertThatCode(() -> projectLeaveService.leave(fixture.projectId(), fixture.userId()))
                .doesNotThrowAnyException();

        assertThat(projectRepository.findById(fixture.projectId())).isEmpty();
        assertThat(projectMemberRepository.count()).isZero();
    }

    @Test
    void nonLastMemberLeaveMarksExitAndKeepsProject() {
        Fixture fixture = saveOwner("survivor-owner");
        Long leaverId = saveActiveMember(fixture, "survivor-member");

        ProjectLeaveResponse response = projectLeaveService.leave(fixture.projectId(), leaverId);

        assertThat(response.success()).isTrue();
        // 프로젝트는 살아남고, 나간 멤버만 EXIT가 된다.
        assertThat(projectRepository.findById(fixture.projectId())).isPresent();
        ProjectMember leaver = projectMemberRepository.findById(leaverId).orElseThrow();
        assertThat(leaver.getStatus()).isEqualTo(MemberStatus.EXIT);
    }

    @Test
    void ownerLeaveDelegatesOwnershipToRemainingMemberAndExits() {
        Fixture fixture = saveOwner("delegating-owner");
        Long successorId = saveActiveMember(fixture, "delegated-member");

        ProjectLeaveResponse response = projectLeaveService.leave(fixture.projectId(), fixture.userId());

        assertThat(response.success()).isTrue();
        // 프로젝트는 유지되고, 남은 멤버가 새 OWNER가 되며, 기존 OWNER는 EXIT 처리된다.
        assertThat(projectRepository.findById(fixture.projectId())).isPresent();
        ProjectMember successor = projectMemberRepository.findById(successorId).orElseThrow();
        assertThat(successor.getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(successor.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        ProjectMember previousOwner = projectMemberRepository
                .findByProjectIdAndUserId(fixture.projectId(), fixture.userId()).orElseThrow();
        assertThat(previousOwner.getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(previousOwner.getStatus()).isEqualTo(MemberStatus.EXIT);
    }

    private Fixture saveOwner(String suffix) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            User user = userRepository.save(User.createLocal(
                    suffix + "@plog.test", "encoded-password", "Owner", suffix));
            Project project = projectRepository.save(Project.builder()
                    .projectName("Plog API")
                    .inviteTokenHash(HashUtil.sha256Hex(INVITE_CODE))
                    .inviteTokenEncrypted("encrypted-invite")
                    .projectType(ProjectType.DEVELOP)
                    .status(ProjectStatus.IN_PROGRESS)
                    .startDay(LocalDate.now())
                    .endDay(LocalDate.now().plusDays(30))
                    .build());
            projectMemberRepository.save(ProjectMember.builder()
                    .user(user)
                    .project(project)
                    .role(ProjectRole.OWNER)
                    .status(MemberStatus.ACTIVE)
                    .build());
            return new Fixture(user.getId(), project.getId());
        });
    }

    private Fixture saveLoneMember(String suffix) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            User user = userRepository.save(User.createLocal(
                    suffix + "@plog.test", "encoded-password", "Member", suffix));
            Project project = projectRepository.save(Project.builder()
                    .projectName("Plog API")
                    .inviteTokenHash(HashUtil.sha256Hex(INVITE_CODE))
                    .inviteTokenEncrypted("encrypted-invite")
                    .projectType(ProjectType.DEVELOP)
                    .status(ProjectStatus.IN_PROGRESS)
                    .startDay(LocalDate.now())
                    .endDay(LocalDate.now().plusDays(30))
                    .build());
            projectMemberRepository.save(ProjectMember.builder()
                    .user(user)
                    .project(project)
                    .role(ProjectRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .build());
            return new Fixture(user.getId(), project.getId());
        });
    }

    private Long saveActiveMember(Fixture fixture, String suffix) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            Project project = projectRepository.findById(fixture.projectId()).orElseThrow();
            User user = userRepository.save(User.createLocal(
                    suffix + "@plog.test", "encoded-password", "Member", suffix));
            return projectMemberRepository.save(ProjectMember.builder()
                    .user(user)
                    .project(project)
                    .role(ProjectRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .build()).getId();
        });
    }

    private record Fixture(Long userId, Long projectId) {
    }
}
