package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.config.IntegrationCollectionProperties;
import com.plog.domain.integration.entity.IntegrationCollectionJob;
import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import com.plog.domain.integration.repository.IntegrationCollectionJobRepository;
import com.plog.domain.notification.event.IntegrationCollectionCompletedEvent;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void 잡_성공_트랜잭션에서_수집_요청자의_완료_이벤트를_발행한다() {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(7L);
        ProjectMember requester = mock(ProjectMember.class);
        given(requester.getId()).willReturn(3L);
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
                42L, 7L, token, 1, CollectionCursor.start());
        given(jobRepository.findByIdForUpdate(42L)).willReturn(Optional.of(entity));
        IntegrationCollectionJobService service = new IntegrationCollectionJobService(
                jobRepository, projectRepository, projectMemberRepository, properties, eventPublisher);

        service.succeed(claimed, Instant.now(), 3, 3);

        ArgumentCaptor<IntegrationCollectionCompletedEvent> captor =
                ArgumentCaptor.forClass(IntegrationCollectionCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new IntegrationCollectionCompletedEvent(7L, 42L, 3L));
        assertThat(entity.getStatus()).isEqualTo(IntegrationCollectionJobStatus.SUCCEEDED);
    }
}
