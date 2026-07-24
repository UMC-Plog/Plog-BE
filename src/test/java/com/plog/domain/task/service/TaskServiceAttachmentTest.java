package com.plog.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.FilePromotionEvent;
import com.plog.infrastructure.s3.FileStorageService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TaskServiceAttachmentTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final Long ASSIGNEE_ID = 5L;
    private static final String FILE_KEY = "temporary/task/users/1/abc/spec.docx";

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskAttachmentRepository taskAttachmentRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private AttachmentPolicy attachmentPolicy;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskAttachmentRepository,
                projectMemberRepository, projectAccessService,
                attachmentPolicy, fileStorageService, eventPublisher);
    }

    private void givenAssignee() {
        Project project = Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.GENERAL)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 1))
                .endDay(LocalDate.of(2026, 8, 31))
                .build();
        ProjectMember assignee = ProjectMember.builder()
                .id(ASSIGNEE_ID)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
        given(projectMemberRepository.findById(ASSIGNEE_ID)).willReturn(Optional.of(assignee));
    }

    private TaskCreateRequest requestWith(TaskCreateRequest.TaskAttachmentRequest... attachments) {
        return new TaskCreateRequest("설계 문서", ASSIGNEE_ID, TaskCategory.ETC,
                TaskStatus.TODO, LocalDate.of(2026, 8, 1), List.of(attachments));
    }

    private TaskCreateRequest.TaskAttachmentRequest fileAttachment() {
        return new TaskCreateRequest.TaskAttachmentRequest(
                AttachmentType.FILE, "spec.docx", 2048L, null, FILE_KEY);
    }

    @Test
    void verifiesUploadedFilesWithTheTaskUsage() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith(fileAttachment()));

        verify(attachmentPolicy).validateFileAttachment(
                AttachmentUsage.TASK, USER_ID, "spec.docx", 2048L, FILE_KEY,
                TaskErrorCode.INVALID_ATTACHMENT);
    }

    @Test
    void publishesAPromotionEventSoUploadedFilesSurviveTheLifecycleRule() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith(fileAttachment()));

        ArgumentCaptor<FilePromotionEvent> captor =
                ArgumentCaptor.forClass(FilePromotionEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().fileKeys()).containsExactly(FILE_KEY);
    }

    @Test
    void doesNotTouchStorageForLinkAttachments() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith(
                new TaskCreateRequest.TaskAttachmentRequest(
                        AttachmentType.LINK, "설계 노션", null, "https://example.com/doc", null)));

        verifyNoInteractions(fileStorageService, eventPublisher);
    }

    @Test
    void rejectsExternalAttachments() {
        givenAssignee();

        assertThatThrownBy(() -> service.createTask(PROJECT_ID, USER_ID, requestWith(
                new TaskCreateRequest.TaskAttachmentRequest(
                        AttachmentType.EXTERNAL, "외부", null, "https://example.com", null))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void delegatesTheCountLimitToTheSharedPolicy() {
        givenAssignee();
        TaskCreateRequest.TaskAttachmentRequest[] eleven =
                IntStream.range(0, 11).mapToObj(i -> fileAttachment())
                        .toArray(TaskCreateRequest.TaskAttachmentRequest[]::new);

        service.createTask(PROJECT_ID, USER_ID, requestWith(eleven));

        verify(attachmentPolicy).validateCount(11, TaskErrorCode.TASK_ATTACHMENT_LIMIT_EXCEEDED);
    }
}
