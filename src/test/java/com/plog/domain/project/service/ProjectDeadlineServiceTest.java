package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.service.IntegrationCollectionJobService;
import com.plog.domain.notification.event.PeerEvaluationStartedEvent;
import com.plog.domain.project.entity.PeerEvaluationStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectCollectionStatus;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.event.InternalActivityCollectionRequestedEvent;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;

class ProjectDeadlineServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectIntegrationRepository integrationRepository = mock(ProjectIntegrationRepository.class);
    private final IntegrationCollectionJobService collectionJobService = mock(IntegrationCollectionJobService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private ProjectDeadlineService service;

    @BeforeEach
    void setUp() {
        service = new ProjectDeadlineService(
                projectRepository, integrationRepository, collectionJobService, eventPublisher);
        IntegrationCollectionJob latestJob = mock(IntegrationCollectionJob.class);
        given(latestJob.getId()).willReturn(42L);
        given(collectionJobService.findLatest(1L)).willReturn(Optional.of(latestJob));
    }

    @Test
    void opensEvaluationAtDeadlineWhenNoExternalToolIsConnected() {
        Project project = project();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));
        given(integrationRepository.hasConnectedIntegration(1L)).willReturn(false);

        service.processDeadline(1L);

        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.OPEN);
        assertThat(project.getInternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.PENDING);
        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.NOT_REQUIRED);
        verify(eventPublisher).publishEvent(new InternalActivityCollectionRequestedEvent(1L));
        verify(eventPublisher).publishEvent(new PeerEvaluationStartedEvent(1L, null));
        verify(collectionJobService, never()).enqueue(1L, null);
    }

    @Test
    void waitsForExternalCollectionWhenAToolIsConnected() {
        Project project = project();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));
        given(integrationRepository.hasConnectedIntegration(1L)).willReturn(true);

        service.processDeadline(1L);

        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.PENDING);
        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.PENDING);
        verify(collectionJobService).enqueue(1L, null);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void opensEvaluationOnlyWhenExternalCollectionSucceeds() {
        Project project = project();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));

        service.completeExternalCollection(1L, 42L, IntegrationCollectionJobStatus.SUCCEEDED);

        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.SUCCEEDED);
        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.OPEN);
        verify(eventPublisher).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void ignoresDuplicateExternalCollectionSuccessAfterEvaluationIsOpen() {
        Project project = project();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));

        service.completeExternalCollection(1L, 42L, IntegrationCollectionJobStatus.SUCCEEDED);
        service.completeExternalCollection(1L, 42L, IntegrationCollectionJobStatus.SUCCEEDED);

        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.SUCCEEDED);
        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.OPEN);
        verify(eventPublisher, times(1)).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void keepsEvaluationPendingWhenExternalCollectionPartiallyFails() {
        Project project = project();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));

        service.completeExternalCollection(1L, 42L, IntegrationCollectionJobStatus.PARTIAL_FAILED);

        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.PARTIAL_FAILED);
        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void keepsEvaluationPendingWhenExternalCollectionFails() {
        Project project = project();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));

        service.completeExternalCollection(1L, 42L, IntegrationCollectionJobStatus.FAILED);

        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.FAILED);
        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void opensEvaluationWithoutNewJobWhenExternalCollectionAlreadySucceeded() {
        Project project = project(ProjectCollectionStatus.SUCCEEDED);
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));
        given(integrationRepository.hasConnectedIntegration(1L)).willReturn(true);

        service.processDeadline(1L);

        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.OPEN);
        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.SUCCEEDED);
        verify(collectionJobService, never()).enqueue(1L, null);
        verify(eventPublisher).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void retriesExternalCollectionWhenPreviousFinalCollectionPartiallyFailed() {
        Project project = project(ProjectCollectionStatus.PARTIAL_FAILED);
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));
        given(integrationRepository.hasConnectedIntegration(1L)).willReturn(true);

        service.processDeadline(1L);

        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.PENDING);
        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.PENDING);
        verify(collectionJobService).enqueue(1L, null);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void retriesExternalCollectionWhenPreviousFinalCollectionFailed() {
        Project project = project(ProjectCollectionStatus.FAILED);
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));
        given(integrationRepository.hasConnectedIntegration(1L)).willReturn(true);

        service.processDeadline(1L);

        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.PENDING);
        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.PENDING);
        verify(collectionJobService).enqueue(1L, null);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @Test
    void startsExternalCollectionFromRetryableTerminalStatus() {
        Project partialFailedProject = project(ProjectCollectionStatus.PARTIAL_FAILED);
        Project failedProject = project(ProjectCollectionStatus.FAILED);

        partialFailedProject.startExternalCollection();
        failedProject.startExternalCollection();

        assertThat(partialFailedProject.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.RUNNING);
        assertThat(failedProject.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.RUNNING);
    }

    @Test
    void ignoresTerminalEventFromOlderCollectionJob() {
        Project project = project(ProjectCollectionStatus.RUNNING);
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));

        service.completeExternalCollection(1L, 41L, IntegrationCollectionJobStatus.FAILED);

        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.RUNNING);
        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    @ParameterizedTest
    @EnumSource(
            value = IntegrationCollectionJobStatus.class,
            names = {"FAILED", "PARTIAL_FAILED"}
    )
    void doesNotOverwriteSucceededCollectionWithLaterFailureEvent(
            IntegrationCollectionJobStatus terminalStatus
    ) {
        Project project = project(ProjectCollectionStatus.SUCCEEDED);
        project.openPeerEvaluation();
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));

        service.completeExternalCollection(1L, 42L, terminalStatus);

        assertThat(project.getExternalCollectionStatus()).isEqualTo(ProjectCollectionStatus.SUCCEEDED);
        assertThat(project.getPeerEvaluationStatus()).isEqualTo(PeerEvaluationStatus.OPEN);
        verify(eventPublisher, never()).publishEvent(new PeerEvaluationStartedEvent(1L, null));
    }

    private Project project() {
        return project(ProjectCollectionStatus.NOT_STARTED);
    }

    private Project project(ProjectCollectionStatus externalCollectionStatus) {
        return Project.builder()
                .id(1L)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(TimeUtil.today().minusDays(10))
                .endDay(TimeUtil.today())
                .externalCollectionStatus(externalCollectionStatus)
                .build();
    }
}
