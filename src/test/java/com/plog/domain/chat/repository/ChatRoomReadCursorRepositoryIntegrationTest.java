package com.plog.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.entity.ChatRoomReadCursor;
import com.plog.domain.chat.repository.projection.ChatRoomUnreadCount;
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
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatRoomReadCursorRepositoryIntegrationTest {

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
    private ChatRoomReadCursorRepository cursorRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

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
    void createsOneNullCursorPerRoomAndProjectMember() {
        Project project = saveProject("cursor-unique");
        ProjectMember member = saveMember(project, "cursor-member", MemberStatus.ACTIVE);
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));

        ChatRoomReadCursor cursor = cursorRepository.saveAndFlush(
                ChatRoomReadCursor.create(room, member)
        );

        assertThat(cursor.getLastReadMessageId()).isNull();
        assertThatThrownBy(() -> cursorRepository.saveAndFlush(
                ChatRoomReadCursor.create(room, member)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void advancesOnlyTheOwnersCursorWithMessagesFromTheSameRoom() {
        Project project = saveProject("cursor-advance");
        ProjectMember member = saveMember(project, "cursor-owner", MemberStatus.ACTIVE);
        ProjectMember otherMember = saveMember(project, "cursor-other", MemberStatus.ACTIVE);
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));
        ChatRoomReadCursor cursor = cursorRepository.saveAndFlush(
                ChatRoomReadCursor.create(room, member)
        );
        ChatMessage first = saveMessage(room, member, "first");
        ChatMessage second = saveMessage(room, member, "second");

        Project otherProject = saveProject("cursor-other-project");
        ProjectMember otherProjectMember = saveMember(
                otherProject, "cursor-other-project-member", MemberStatus.ACTIVE
        );
        ChatRoom otherRoom = chatRoomRepository.save(ChatRoom.create(otherProject));
        ChatMessage otherRoomMessage = saveMessage(otherRoom, otherProjectMember, "other-room");

        assertThat(cursorRepository.advance(
                room.getId(), otherMember.getUser().getId(), first.getId(), MemberStatus.ACTIVE
        )).isZero();
        assertThat(cursorRepository.advance(
                room.getId(), member.getUser().getId(), otherRoomMessage.getId(), MemberStatus.ACTIVE
        )).isZero();
        assertThat(cursorRepository.advance(
                room.getId(), member.getUser().getId(), first.getId(), MemberStatus.ACTIVE
        )).isOne();
        assertThat(cursorRepository.advance(
                room.getId(), member.getUser().getId(), second.getId(), MemberStatus.ACTIVE
        )).isOne();
        assertThat(cursorRepository.advance(
                room.getId(), member.getUser().getId(), first.getId(), MemberStatus.ACTIVE
        )).isZero();
        assertThat(cursorRepository.findById(cursor.getId()).orElseThrow().getLastReadMessageId())
                .isEqualTo(second.getId());
    }

    @Test
    void countsUnreadMessagesForActiveMembersFromTheirCursor() {
        Project project = saveProject("cursor-unread");
        ProjectMember member = saveMember(project, "cursor-unread-member", MemberStatus.ACTIVE);
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));
        cursorRepository.saveAndFlush(ChatRoomReadCursor.create(room, member));

        ChatRoomUnreadCount empty = cursorRepository.findUnreadCount(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        ).orElseThrow();
        assertThat(empty.getUnreadCount()).isZero();

        ChatMessage first = saveMessage(room, member, "first-unread");
        saveMessage(room, member, "second-unread");

        ChatRoomUnreadCount beforeRead = cursorRepository.findUnreadCount(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        ).orElseThrow();
        assertThat(beforeRead.getUnreadCount()).isEqualTo(2L);

        cursorRepository.advance(
                room.getId(), member.getUser().getId(), first.getId(), MemberStatus.ACTIVE
        );

        ChatRoomUnreadCount afterRead = cursorRepository.findUnreadCount(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        ).orElseThrow();
        assertThat(afterRead.getChatRoomId()).isEqualTo(room.getId());
        assertThat(afterRead.getUnreadCount()).isOne();
    }

    @Test
    void rejectsCursorCreationAcrossProjects() {
        Project roomProject = saveProject("cursor-room-project");
        Project memberProject = saveProject("cursor-member-project");
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(roomProject));
        ProjectMember member = saveMember(
                memberProject, "cursor-wrong-project-member", MemberStatus.ACTIVE
        );

        assertThatThrownBy(() -> ChatRoomReadCursor.create(room, member))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blocksExitedMembersAndPreservesTheirCursorAfterReactivation() {
        Project project = saveProject("cursor-reactivate");
        ProjectMember member = saveMember(project, "cursor-reactivate-member", MemberStatus.ACTIVE);
        ProjectMember otherMember = saveMember(project, "cursor-reactivate-other", MemberStatus.ACTIVE);
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));
        ChatRoomReadCursor cursor = cursorRepository.saveAndFlush(
                ChatRoomReadCursor.create(room, member)
        );
        ChatMessage message = saveMessage(room, member, "read-before-exit");
        cursorRepository.advance(
                room.getId(), member.getUser().getId(), message.getId(), MemberStatus.ACTIVE
        );

        assertThat(cursorRepository.findAccessibleCursor(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        )).isPresent();
        assertThat(cursorRepository.findAccessibleCursor(
                room.getId(), otherMember.getUser().getId(), MemberStatus.ACTIVE
        )).isEmpty();

        jdbcTemplate.update(
                "update project_members set project_status = 'EXIT' where project_member_id = ?",
                member.getId()
        );
        entityManager.clear();

        assertThat(cursorRepository.findAccessibleCursor(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        )).isEmpty();
        assertThat(cursorRepository.findUnreadCount(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        )).isEmpty();
        assertThat(cursorRepository.advance(
                room.getId(), member.getUser().getId(), message.getId(), MemberStatus.ACTIVE
        )).isZero();

        ProjectMember exitedMember = projectMemberRepository.findById(member.getId()).orElseThrow();
        exitedMember.reactivateAsMember();
        projectMemberRepository.saveAndFlush(exitedMember);
        entityManager.clear();

        ChatRoomReadCursor reactivatedCursor = cursorRepository.findAccessibleCursor(
                room.getId(), member.getUser().getId(), MemberStatus.ACTIVE
        ).orElseThrow();
        assertThat(reactivatedCursor.getId()).isEqualTo(cursor.getId());
        assertThat(reactivatedCursor.getLastReadMessageId()).isEqualTo(message.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void retainsTheHighestMessageIdWhenCursorAdvancesRace() throws Exception {
        Project project = saveProject("cursor-race");
        ProjectMember member = saveMember(project, "cursor-race-member", MemberStatus.ACTIVE);
        ChatRoom room = chatRoomRepository.save(ChatRoom.create(project));
        ChatRoomReadCursor cursor = cursorRepository.save(
                ChatRoomReadCursor.create(room, member)
        );
        ChatMessage first = saveMessage(room, member, "race-first");
        ChatMessage second = saveMessage(room, member, "race-second");
        Long roomId = room.getId();
        Long userId = member.getUser().getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> lowerAdvance = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cursorRepository.advance(
                        roomId, userId, first.getId(), MemberStatus.ACTIVE
                );
            });
            Future<Integer> higherAdvance = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cursorRepository.advance(
                        roomId, userId, second.getId(), MemberStatus.ACTIVE
                );
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            lowerAdvance.get(10, TimeUnit.SECONDS);
            higherAdvance.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(cursorRepository.findById(cursor.getId()).orElseThrow().getLastReadMessageId())
                .isEqualTo(second.getId());
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

    private ProjectMember saveMember(Project project, String suffix, MemberStatus status) {
        User user = userRepository.save(User.createLocal(
                suffix + "@plog.test",
                "encoded-password",
                "User " + suffix,
                "nickname-" + suffix
        ));
        return projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .role(ProjectRole.MEMBER)
                .status(status)
                .build());
    }

    private ChatMessage saveMessage(ChatRoom room, ProjectMember member, String message) {
        return chatMessageRepository.saveAndFlush(ChatMessage.builder()
                .chatRoom(room)
                .projectMember(member)
                .message(message)
                .build());
    }
}
