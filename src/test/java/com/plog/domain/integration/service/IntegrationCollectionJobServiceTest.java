package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.event.ExternalCollectionFinishedEvent;
import com.plog.domain.integration.event.ExternalCollectionStartedEvent;
import com.plog.domain.integration.repository.IntegrationCollectionJobRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectCollectionStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class IntegrationCollectionJobServiceTest {

    @Mock private IntegrationCollectionJobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private IntegrationCollectionProperties properties;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void 최종_수집으로_전환된_재시도_잡을_즉시_실행_가능하게_한다() {
        Project project = mock(Project.class);
        given(project.getExternalCollectionStatus()).willReturn(ProjectCollectionStatus.PENDING);
        ProjectMember requester = mock(ProjectMember.class);
        Instant deferredUntil = Instant.now().plusSeconds(3_600);
        IntegrationCollectionJob activeJob = IntegrationCollectionJob.builder()
                .id(42L)
                .project(project)
                .requestedByProjectMember(requester)
                .status(IntegrationCollectionJobStatus.RETRYABLE)
                .availableAt(deferredUntil)
                .attemptCount(4)
                .build();
        given(projectRepository.findByIdForUpdate(7L)).willReturn(Optional.of(project));
        given(jobRepository.findByProjectIdAndStatuses(eq(7L), any()))
                .willReturn(List.of(activeJob));
        IntegrationCollectionJobService service = service();

        IntegrationCollectionJob result = service.enqueue(7L, null);

        assertThat(result).isSameAs(activeJob);
        assertThat(activeJob.getAvailableAt()).isBefore(deferredUntil);
    }

    @Test
    void 수동_잡이라도_프로젝트가_최종_수집_중이면_최종_수집으로_claim한다() {
        Instant now = Instant.parse("2026-08-11T18:00:00Z");
        Project project = mock(Project.class);
        given(project.getId()).willReturn(7L);
        given(project.getExternalCollectionStatus()).willReturn(ProjectCollectionStatus.PENDING);
        IntegrationCollectionJob job = IntegrationCollectionJob.builder()
                .id(42L)
                .project(project)
                .requestedByProjectMember(mock(ProjectMember.class))
                .status(IntegrationCollectionJobStatus.RETRYABLE)
                .availableAt(now)
                .attemptCount(1)
                .build();
        given(jobRepository.findDueForUpdate(any(), eq(now), any())).willReturn(List.of(job));
        IntegrationCollectionJobService service = service();

        IntegrationCollectionJobService.ClaimedJob claimed = service.claimNext(now);

        assertThat(claimed.finalCollection()).isTrue();
        verify(eventPublisher).publishEvent(new ExternalCollectionStartedEvent(7L));
    }

    @Test
    void 실행_중인_수동_잡도_프로젝트가_마감_수집으로_전환되면_최종_수집으로_판단한다() {
        Project project = mock(Project.class);
        given(project.getExternalCollectionStatus()).willReturn(ProjectCollectionStatus.PENDING);
        given(projectRepository.findById(7L)).willReturn(Optional.of(project));
        IntegrationCollectionJobService.ClaimedJob claimed = new IntegrationCollectionJobService.ClaimedJob(
                42L, 7L, "token", 1, false, CollectionCursor.start());
        IntegrationCollectionJobService service = service();

        assertThat(service.isFinalCollectionExpected(claimed)).isTrue();
    }

    @Test
    void 수동_잡_성공만으로_피어평가_준비_알림을_발행하지_않는다() {
        Project project = mock(Project.class);
        ProjectMember requester = mock(ProjectMember.class);
        IntegrationCollectionJob entity = IntegrationCollectionJob.builder()
                .id(42L)
                .project(project)
                .requestedByProjectMember(requester)
                .status(IntegrationCollectionJobStatus.PENDING)
                .availableAt(Instant.EPOCH)
                .attemptCount(0)
                .build();
        String token = entity.begin(Instant.now());
        IntegrationCollectionJobService.ClaimedJob claimed = new IntegrationCollectionJobService.ClaimedJob(
                42L, 7L, token, 1, false, CollectionCursor.start());
        given(jobRepository.findByIdForUpdate(42L)).willReturn(Optional.of(entity));
        IntegrationCollectionJobService service = new IntegrationCollectionJobService(
                jobRepository, projectRepository, projectMemberRepository, properties, eventPublisher);

        service.succeed(claimed, Instant.now(), 3, 3);

        verify(eventPublisher, never()).publishEvent(any());
        assertThat(entity.getStatus()).isEqualTo(IntegrationCollectionJobStatus.SUCCEEDED);
    }

    @Test
    void 마감_최종_수집이_실패_상태여도_재수집_잡은_최종_수집으로_판단한다() {
        Project project = mock(Project.class);
        given(project.getExternalCollectionStatus()).willReturn(ProjectCollectionStatus.FAILED);
        given(projectRepository.findById(7L)).willReturn(Optional.of(project));
        IntegrationCollectionJobService.ClaimedJob claimed = new IntegrationCollectionJobService.ClaimedJob(
                42L, 7L, "token", 1, false, CollectionCursor.start());
        IntegrationCollectionJobService service = service();

        assertThat(service.isFinalCollectionExpected(claimed)).isTrue();
    }

    @Test
    void 자동_최종_수집이_끝나면_평가_오픈용_이벤트를_발행한다() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(7L);
        IntegrationCollectionJob entity = IntegrationCollectionJob.builder()
                .id(42L)
                .project(project)
                .status(IntegrationCollectionJobStatus.PENDING)
                .availableAt(Instant.EPOCH)
                .attemptCount(0)
                .build();
        String token = entity.begin(Instant.now());
        IntegrationCollectionJobService.ClaimedJob claimed = new IntegrationCollectionJobService.ClaimedJob(
                42L, 7L, token, 1, true, CollectionCursor.start());
        given(jobRepository.findByIdForUpdate(42L)).willReturn(Optional.of(entity));
        IntegrationCollectionJobService service = new IntegrationCollectionJobService(
                jobRepository, projectRepository, projectMemberRepository, properties, eventPublisher);

        service.succeed(claimed, Instant.now(), 3, 3);

        verify(eventPublisher).publishEvent(
                new ExternalCollectionFinishedEvent(7L, IntegrationCollectionJobStatus.SUCCEEDED));
    }

    private IntegrationCollectionJobService service() {
        return new IntegrationCollectionJobService(
                jobRepository, projectRepository, projectMemberRepository, properties, eventPublisher);
    }
}
