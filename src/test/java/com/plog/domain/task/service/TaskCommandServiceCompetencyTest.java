package com.plog.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.request.TaskUpdateRequest;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskCompetencyClassification;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.UploadedFileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TaskCommandServiceCompetencyTest {

    private static final long PROJECT_ID = 10L;
    private static final long USER_ID = 1L;
    @Mock TaskRepository taskRepository;
    @Mock TaskAttachmentRepository taskAttachmentRepository;
    @Mock ProjectMemberRepository projectMemberRepository;
    @Mock ProjectAccessService projectAccessService;
    @Mock AttachmentPolicy attachmentPolicy;
    @Mock UploadedFileService uploadedFileService;
    @Mock TaskAttachmentUrlResolver urlResolver;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock TaskTitleCompetencyClassifier classifier;
    private TaskCommandService service;
    private ProjectMember assignee;

    @BeforeEach
    void setUp() {
        service = new TaskCommandService(taskRepository, taskAttachmentRepository, projectMemberRepository,
                projectAccessService, attachmentPolicy, uploadedFileService, urlResolver, eventPublisher, classifier);
        Project project = Project.builder().id(PROJECT_ID).projectName("Plog")
                .inviteTokenHash("hash").inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.GENERAL).status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.now()).endDay(LocalDate.now().plusDays(30)).build();
        assignee = ProjectMember.builder().id(5L).project(project).role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE).build();
    }

    @Test
    void savesClassificationWhenCreatingTask() {
        when(projectMemberRepository.findById(5L)).thenReturn(Optional.of(assignee));
        when(classifier.classify("로그인 API 구현")).thenReturn(classification(CompetencyCategory.OUTPUT));

        var response = service.createTask(PROJECT_ID, USER_ID, createRequest("로그인 API 구현"));

        assertThat(response.inferredCompetency()).isEqualTo(CompetencyCategory.OUTPUT);
        assertThat(response.competencyConfidence()).isEqualByComparingTo("0.8765");
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void updatesClassificationWhenTitleChanges() {
        Task task = Task.create(assignee, "기존 제목", TaskCategory.ETC, TaskStatus.TODO, LocalDate.now());
        when(taskRepository.findByIdAndProjectMember_Project_Id(20L, PROJECT_ID)).thenReturn(Optional.of(task));
        when(taskAttachmentRepository.findAllByTaskId(20L)).thenReturn(List.of());
        when(classifier.classify("회의 일정 조율")).thenReturn(classification(CompetencyCategory.COLLABORATION));

        var response = service.updateTask(PROJECT_ID, 20L, USER_ID,
                new TaskUpdateRequest("회의 일정 조율", null, null, null));

        assertThat(response.inferredCompetency()).isEqualTo(CompetencyCategory.COLLABORATION);
        verify(classifier).classify("회의 일정 조율");
    }

    @Test
    void doesNotCallEmbeddingAgainWhenTitleIsUnchanged() {
        Task task = Task.create(assignee, "같은 제목", TaskCategory.ETC, TaskStatus.TODO, LocalDate.now());
        task.applyCompetencyClassification(classification(CompetencyCategory.LEADERSHIP));
        when(taskRepository.findByIdAndProjectMember_Project_Id(20L, PROJECT_ID)).thenReturn(Optional.of(task));
        when(taskAttachmentRepository.findAllByTaskId(20L)).thenReturn(List.of());

        var response = service.updateTask(PROJECT_ID, 20L, USER_ID,
                new TaskUpdateRequest("같은 제목", null, null, null));

        verify(classifier, never()).classify(any());
        assertThat(response.inferredCompetency()).isEqualTo(CompetencyCategory.LEADERSHIP);
    }

    @Test
    void creationSucceedsWithNullClassificationWhenEmbeddingFails() {
        when(projectMemberRepository.findById(5L)).thenReturn(Optional.of(assignee));
        when(classifier.classify(any())).thenThrow(new RuntimeException("embedding unavailable"));

        var response = service.createTask(PROJECT_ID, USER_ID, createRequest("로그인 API 구현"));

        assertThat(response.inferredCompetency()).isNull();
        assertThat(response.competencyConfidence()).isNull();
        assertThat(response.competencyClassifierVersion()).isNull();
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void updateSucceedsAndClearsStaleClassificationWhenEmbeddingFails() {
        Task task = Task.create(assignee, "기존 제목", TaskCategory.ETC, TaskStatus.TODO, LocalDate.now());
        task.applyCompetencyClassification(classification(CompetencyCategory.OUTPUT));
        when(taskRepository.findByIdAndProjectMember_Project_Id(20L, PROJECT_ID)).thenReturn(Optional.of(task));
        when(taskAttachmentRepository.findAllByTaskId(20L)).thenReturn(List.of());
        when(classifier.classify("새 제목")).thenThrow(new RuntimeException("embedding unavailable"));

        var response = service.updateTask(PROJECT_ID, 20L, USER_ID,
                new TaskUpdateRequest("새 제목", null, null, null));

        assertThat(response.title()).isEqualTo("새 제목");
        assertThat(response.inferredCompetency()).isNull();
        assertThat(response.competencyConfidence()).isNull();
        assertThat(response.competencyClassifierVersion()).isNull();
    }

    private TaskCreateRequest createRequest(String title) {
        return new TaskCreateRequest(title, 5L, TaskCategory.ETC, TaskStatus.TODO,
                LocalDate.now().plusDays(3), List.of());
    }

    private TaskCompetencyClassification classification(CompetencyCategory category) {
        return new TaskCompetencyClassification(category, new BigDecimal("0.8765"),
                TaskTitleCompetencyClassifier.CLASSIFIER_VERSION);
    }
}
