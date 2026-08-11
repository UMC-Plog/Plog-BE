package com.plog.domain.task.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.task.dto.request.TaskAttachmentAddRequest;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskCompetencyClassification;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.event.TaskAttachmentAddedEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
    private ReportActivityLogRepository reportActivityLogRepository;

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TaskTitleCompetencyClassifier competencyClassifier;

    private TaskCommandService service;

    @BeforeEach
    void setUp() {
        service = new TaskCommandService(taskRepository, taskAttachmentRepository,
                reportActivityLogRepository,
                projectMemberRepository, projectAccessService,
                attachmentPolicy, uploadedFileService, urlResolver, eventPublisher, competencyClassifier);
        lenient().when(competencyClassifier.classify(any()))
                .thenReturn(TaskCompetencyClassification.unclassified());
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

    @Test
    void 카드_생성시_동봉한_첨부마다_리포트_이벤트를_발행한다() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith(fileAttachment(), fileAttachment()));

        ArgumentCaptor<TaskAttachmentAddedEvent> captor = ArgumentCaptor.forClass(TaskAttachmentAddedEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        captor.getAllValues().forEach(event ->
                org.assertj.core.api.Assertions.assertThat(event.projectMemberId()).isEqualTo(ASSIGNEE_ID));
    }

    @Test
    void 첨부가_없으면_리포트_이벤트를_발행하지_않는다() {
        givenAssignee();

        service.createTask(PROJECT_ID, USER_ID, requestWith());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 기존_카드에_첨부를_추가하면_리포트_이벤트를_발행한다() {
        Long taskId = 100L;
        ProjectMember assignee = ProjectMember.builder().id(ASSIGNEE_ID).build();
        Task task = Task.builder().id(taskId).projectMember(assignee).build();
        given(taskRepository.findByIdAndProjectMember_Project_Id(taskId, PROJECT_ID))
                .willReturn(Optional.of(task));

        service.addAttachment(PROJECT_ID, taskId, USER_ID, new TaskAttachmentAddRequest(
                AttachmentType.LINK, "설계 노션", null, "https://example.com/doc", null));

        ArgumentCaptor<TaskAttachmentAddedEvent> captor = ArgumentCaptor.forClass(TaskAttachmentAddedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        TaskAttachmentAddedEvent event = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.taskId()).isEqualTo(taskId);
        org.assertj.core.api.Assertions.assertThat(event.projectMemberId()).isEqualTo(ASSIGNEE_ID);
    }

    @Test
    void 업무_삭제시_연결된_리포트_활동로그를_업무보다_먼저_삭제한다() {
        Long taskId = 100L;
        Project project = org.mockito.Mockito.mock(Project.class);
        given(project.getId()).willReturn(PROJECT_ID);
        ProjectMember assignee = ProjectMember.builder().project(project).build();
        Task task = Task.builder().id(taskId).projectMember(assignee).build();
        given(taskRepository.findByIdForUpdate(taskId)).willReturn(Optional.of(task));
        given(taskAttachmentRepository.findAllByTaskId(taskId)).willReturn(List.of());
        given(taskAttachmentRepository.findFileIdsByTaskId(taskId)).willReturn(List.of());

        service.deleteTask(PROJECT_ID, taskId, USER_ID);

        InOrder deletionOrder = inOrder(reportActivityLogRepository, taskRepository);
        deletionOrder.verify(reportActivityLogRepository).acquireSourceLock("TASK:task:" + taskId);
        deletionOrder.verify(taskRepository).findByIdForUpdate(taskId);
        deletionOrder.verify(reportActivityLogRepository).deleteAllByLinkedTaskId(taskId);
        deletionOrder.verify(taskRepository).delete(task);
    }
}
