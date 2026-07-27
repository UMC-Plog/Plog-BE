package com.plog.domain.task.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.UploadedFileService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCommandServiceAttachmentTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final Long ASSIGNEE_ID = 5L;
    private static final String FILE_KEY = "tasks/users/1/abc/spec.docx";

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
    private UploadedFileService uploadedFileService;

    @Mock
    private TaskAttachmentUrlResolver urlResolver;

    private TaskCommandService service;

    @BeforeEach
    void setUp() {
        service = new TaskCommandService(taskRepository, taskAttachmentRepository,
                projectMemberRepository, projectAccessService,
                attachmentPolicy, uploadedFileService, urlResolver);
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
    void confirmsUploadedFilesWithTheTaskUsage() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith(fileAttachment()));

        verify(attachmentPolicy).confirmFileAttachment(
                AttachmentUsage.TASK, USER_ID, "spec.docx", 2048L, FILE_KEY,
                TaskErrorCode.INVALID_ATTACHMENT);
    }

    @Test
    void doesNotConfirmAnythingForLinkAttachments() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith(
                new TaskCreateRequest.TaskAttachmentRequest(
                        AttachmentType.LINK, "설계 노션", null, "https://example.com/doc", null)));

        verify(attachmentPolicy, never())
                .confirmFileAttachment(any(), any(), any(), any(), any(), any());
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