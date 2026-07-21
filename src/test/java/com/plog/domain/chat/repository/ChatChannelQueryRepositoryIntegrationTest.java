package com.plog.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.entity.ChatRoomReadCursor;
import com.plog.domain.chat.repository.projection.ChatChannelSummary;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ChatChannelQueryRepositoryIntegrationTest {

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
    private ChatRoomReadCursorRepository cursorRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsOnlyActiveChannelsWithLatestMessageAndUnreadCount() {
        User user = saveUser("channel-owner");
        ChatFixture recent = saveChannel(user, "recent", MemberStatus.ACTIVE);
        ChatFixture older = saveChannel(user, "older", MemberStatus.ACTIVE);
        ChatFixture firstEmpty = saveChannel(user, "empty-first", MemberStatus.ACTIVE);
        ChatFixture secondEmpty = saveChannel(user, "empty-second", MemberStatus.ACTIVE);
        saveChannel(user, "exited", MemberStatus.EXIT);

        ChatMessage read = saveMessage(recent, "read", LocalDateTime.of(2026, 7, 1, 10, 0));
        ChatMessage latest = saveMessage(recent, "latest", LocalDateTime.of(2026, 7, 3, 10, 0));
        saveMessage(older, "older-message", LocalDateTime.of(2026, 7, 2, 10, 0));
        cursorRepository.saveAndFlush(ChatRoomReadCursor.create(
                chatRoomRepository.findById(recent.room().getId()).orElseThrow(),
                projectMemberRepository.findById(recent.member().getId()).orElseThrow()
        ));
        assertThat(cursorRepository.advance(
                recent.room().getId(), user.getId(), read.getId(), MemberStatus.ACTIVE.name()
        )).isOne();
        entityManager.clear();

        Slice<ChatChannelSummary> firstPage = chatRoomRepository.findChannelPage(
                user.getId(), MemberStatus.ACTIVE, PageRequest.of(0, 2)
        );
        Slice<ChatChannelSummary> secondPage = chatRoomRepository.findChannelPage(
                user.getId(), MemberStatus.ACTIVE, PageRequest.of(1, 2)
        );

        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent()).extracting(ChatChannelSummary::getRoomId)
                .containsExactly(recent.room().getId(), older.room().getId());
        assertThat(firstPage.getContent().getFirst().getLatestMessage()).isEqualTo("latest");
        assertThat(firstPage.getContent().getFirst().getLatestMessageAt())
                .isEqualTo(latest.getCreatedAt());
        assertThat(firstPage.getContent().getFirst().getUnreadMessageCount()).isOne();
        assertThat(firstPage.getContent().get(1).getUnreadMessageCount()).isOne();
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.getContent()).extracting(ChatChannelSummary::getRoomId)
                .containsExactly(firstEmpty.room().getId(), secondEmpty.room().getId());
        assertThat(secondPage.getContent().getFirst().getLatestMessage()).isNull();
        assertThat(secondPage.getContent().getFirst().getLatestMessageAt()).isNull();
        assertThat(secondPage.getContent().getFirst().getUnreadMessageCount()).isZero();
    }

    @Test
    void returnsAnEmptyPageForAUserWithoutActiveChannels() {
        User outsider = saveUser("channel-outsider");

        Slice<ChatChannelSummary> page = chatRoomRepository.findChannelPage(
                outsider.getId(), MemberStatus.ACTIVE, PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).isEmpty();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void searchesOnlyActiveUserChannelsByProjectNameIgnoringCase() {
        User user = saveUser("channel-search");
        ChatFixture first = saveChannel(user, "PLOG API", MemberStatus.ACTIVE);
        ChatFixture second = saveChannel(user, "mobile plog", MemberStatus.ACTIVE);
        saveChannel(user, "other", MemberStatus.ACTIVE);
        saveChannel(user, "Plog exited", MemberStatus.EXIT);
        saveChannel(saveUser("channel-search-outsider"), "Plog outsider", MemberStatus.ACTIVE);

        Slice<ChatChannelSummary> page = chatRoomRepository.findChannelPageByProjectName(
                user.getId(),
                MemberStatus.ACTIVE,
                "%plog%",
                PageRequest.of(0, 20)
        );

        assertThat(page.hasNext()).isFalse();
        assertThat(page.getContent()).extracting(ChatChannelSummary::getRoomId)
                .containsExactly(first.room().getId(), second.room().getId());
    }

    @Test
    void treatsLikeWildcardsAndEscapeCharacterAsProjectNameLiterals() {
        User user = saveUser("channel-search-literal");
        ChatFixture literal = saveChannel(user, "rate_100%!", MemberStatus.ACTIVE);
        saveChannel(user, "rateA100B!", MemberStatus.ACTIVE);
        saveChannel(user, "rate_100%X", MemberStatus.ACTIVE);

        Slice<ChatChannelSummary> page = chatRoomRepository.findChannelPageByProjectName(
                user.getId(),
                MemberStatus.ACTIVE,
                "%rate!_100!%!!%",
                PageRequest.of(0, 20)
        );

        assertThat(page.hasNext()).isFalse();
        assertThat(page.getContent()).extracting(ChatChannelSummary::getRoomId)
                .containsExactly(literal.room().getId());
    }

    private ChatFixture saveChannel(User user, String suffix, MemberStatus status) {
        Project project = saveProject(suffix);
        ProjectMember member = projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .status(status)
                .build());
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));
        return new ChatFixture(room, member);
    }

    private ChatMessage saveMessage(ChatFixture fixture, String message, LocalDateTime createdAt) {
        ChatRoom room = chatRoomRepository.findById(fixture.room().getId()).orElseThrow();
        ProjectMember member = projectMemberRepository.findById(fixture.member().getId()).orElseThrow();
        long sequence = room.issueNextMessageSequence();
        ChatMessage saved = chatMessageRepository.saveAndFlush(ChatMessage.create(
                room, member, message, sequence
        ));
        jdbcTemplate.update(
                "update chat_messages set created_at = ?, updated_at = ? where chat_id = ?",
                createdAt,
                createdAt,
                saved.getId()
        );
        entityManager.clear();
        return chatMessageRepository.findById(saved.getId()).orElseThrow();
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

    private record ChatFixture(ChatRoom room, ProjectMember member) {
    }
}
