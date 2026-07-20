package com.plog.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
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
class ChatRoomRepositoryIntegrationTest {

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
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void allowsOnlyOneChatRoomPerProject() {
        Project project = saveProject("unique-room");
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.create(project));

        assertThat(chatRoomRepository.findByProjectId(project.getId()))
                .contains(room);
        assertThatThrownBy(() -> chatRoomRepository.saveAndFlush(ChatRoom.create(project)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsARoomOnlyForActiveProjectMembers() {
        Project project = saveProject("access-room");
        User activeUser = saveUser("active");
        User exitedUser = saveUser("exited");
        User outsider = saveUser("outsider");
        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(activeUser)
                .role(ProjectRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .build());
        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(exitedUser)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.EXIT)
                .build());
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.create(project));

        assertThat(chatRoomRepository.findAccessibleRoom(
                room.getId(), activeUser.getId(), MemberStatus.ACTIVE
        )).contains(room);
        assertThat(chatRoomRepository.findAccessibleRoom(
                room.getId(), exitedUser.getId(), MemberStatus.ACTIVE
        )).isEmpty();
        assertThat(chatRoomRepository.findAccessibleRoom(
                room.getId(), outsider.getId(), MemberStatus.ACTIVE
        )).isEmpty();
    }

    @Test
    void queriesMessagesByRoomWhileAllowingLegacyRowsWithoutARoom() {
        Project project = saveProject("message-room");
        User user = saveUser("message-member");
        ProjectMember member = projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.OWNER)
                .status(MemberStatus.ACTIVE)
                .build());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));
        ChatMessage first = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .chatRoom(room)
                .projectMember(member)
                .message("first")
                .build());
        ChatMessage second = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .chatRoom(room)
                .projectMember(member)
                .message("second")
                .build());
        ChatMessage legacy = chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .projectMember(member)
                .message("legacy")
                .build());

        List<ChatMessage> messages = chatMessageRepository
                .findAllByChatRoomIdOrderByCreatedAtAscIdAsc(room.getId());

        assertThat(messages).extracting(ChatMessage::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(chatMessageRepository.findById(legacy.getId()).orElseThrow().getChatRoom())
                .isNull();
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

    private User saveUser(String suffix) {
        return userRepository.save(User.createLocal(
                suffix + "@plog.test",
                "encoded-password",
                "User " + suffix,
                "nickname-" + suffix
        ));
    }
}
