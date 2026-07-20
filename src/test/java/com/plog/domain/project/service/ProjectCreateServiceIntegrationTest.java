package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
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
import com.plog.global.util.HashUtil;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectCreateServiceIntegrationTest {

    private static final String INVITE_BASE_URL = "https://plog.test/invites";
    private static final String RAW_INVITE_TOKEN = "project-create-invite-token";

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
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private InviteTokenCipher inviteTokenCipher;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        String encryptionKey = Base64.getEncoder().encodeToString(new byte[32]);
        inviteTokenCipher = new InviteTokenCipher(encryptionKey);
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
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
    void persistsTheProjectOwnerAndInviteInOneCommittedTransaction() {
        User creator = saveCreator("success");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate endDay = today.plusDays(30);
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(RAW_INVITE_TOKEN);
        ProjectCreateService service = createService(generator, projectMemberRepository);

        ProjectCreateResponse response = service.create(
                creator.getId(),
                new ProjectCreateRequest("  Plog API  ", ProjectType.DEVELOP, endDay)
        );

        assertThat(projectRepository.count()).isEqualTo(1);
        assertThat(projectMemberRepository.count()).isEqualTo(1);
        Project storedProject = projectRepository.findById(response.projectId()).orElseThrow();
        assertThat(storedProject.getProjectName()).isEqualTo("Plog API");
        assertThat(storedProject.getProjectType()).isEqualTo(ProjectType.DEVELOP);
        assertThat(storedProject.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(storedProject.getStartDay()).isEqualTo(today);
        assertThat(storedProject.getEndDay()).isEqualTo(endDay);
        assertThat(storedProject.getInviteTokenHash()).isEqualTo(HashUtil.sha256Hex(RAW_INVITE_TOKEN));
        assertThat(storedProject.getInviteTokenHash()).isNotEqualTo(RAW_INVITE_TOKEN);
        assertThat(storedProject.getInviteTokenEncrypted()).isNotEqualTo(RAW_INVITE_TOKEN);
        assertThat(inviteTokenCipher.decrypt(storedProject.getInviteTokenEncrypted()))
                .isEqualTo(RAW_INVITE_TOKEN);

        MembershipSnapshot membership = transactionTemplate.execute(status -> {
            ProjectMember member = projectMemberRepository.findById(response.myProjectMemberId())
                    .orElseThrow();
            return new MembershipSnapshot(
                    member.getUser().getId(),
                    member.getProject().getId(),
                    member.getRole(),
                    member.getStatus()
            );
        });
        assertThat(membership.userId()).isEqualTo(creator.getId());
        assertThat(membership.projectId()).isEqualTo(response.projectId());
        assertThat(membership.role()).isEqualTo(ProjectRole.OWNER);
        assertThat(membership.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.invite().inviteCode()).isEqualTo(RAW_INVITE_TOKEN);
        assertThat(response.invite().inviteUrl()).isEqualTo(INVITE_BASE_URL + "/" + RAW_INVITE_TOKEN);
        verify(generator).generate();
    }

    @Test
    void rollsBackTheProjectAndInviteWhenOwnerPersistenceFails() {
        User creator = saveCreator("rollback");
        InviteTokenGenerator generator = mock(InviteTokenGenerator.class);
        given(generator.generate()).willReturn(RAW_INVITE_TOKEN);
        ProjectMemberRepository failingMemberRepository = mock(ProjectMemberRepository.class);
        DataIntegrityViolationException memberFailure =
                new DataIntegrityViolationException("owner persistence failed");
        given(failingMemberRepository.save(any(ProjectMember.class))).willThrow(memberFailure);
        ProjectCreateService service = createService(generator, failingMemberRepository);

        assertThatThrownBy(() -> service.create(
                creator.getId(),
                new ProjectCreateRequest(
                        "Plog API",
                        ProjectType.GENERAL,
                        LocalDate.now(ZoneOffset.UTC).plusDays(30)
                )
        )).isSameAs(memberFailure);

        assertThat(projectRepository.count()).isZero();
        assertThat(projectMemberRepository.count()).isZero();
        assertThat(projectRepository.findByInviteTokenHash(HashUtil.sha256Hex(RAW_INVITE_TOKEN)))
                .isEmpty();
        assertThat(userRepository.count()).isEqualTo(1);
        verify(generator).generate();
    }

    private User saveCreator(String suffix) {
        return transactionTemplate.execute(status -> userRepository.save(User.createLocal(
                suffix + "@plog.test",
                "encoded-password",
                "Creator",
                "creator-" + suffix
        )));
    }

    private ProjectCreateService createService(
            InviteTokenGenerator generator,
            ProjectMemberRepository memberRepository
    ) {
        InviteTokenService inviteTokenService = new InviteTokenService(
                projectRepository,
                generator,
                inviteTokenCipher,
                transactionManager
        );
        return new ProjectCreateService(
                userRepository,
                projectRepository,
                memberRepository,
                inviteTokenService,
                INVITE_BASE_URL
        );
    }

    private record MembershipSnapshot(
            Long userId,
            Long projectId,
            ProjectRole role,
            MemberStatus status
    ) {
    }
}
