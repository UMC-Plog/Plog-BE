package com.plog.domain.integration.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectIntegrationServiceTest {

    @Mock
    private ProjectIntegrationRepository projectIntegrationRepository;

    @Mock
    private IntegrationCredentialCipher credentialCipher;

    @Mock
    private ProjectRepository projectRepository;

    @Test
    void rejectsStartingWorkspaceIntegrationAfterProjectCompletion() {
        ProjectIntegrationService service = new ProjectIntegrationService(
                projectIntegrationRepository,
                credentialCipher,
                projectRepository
        );
        Project project = mock(Project.class);
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(project.isCompleted()).willReturn(true);

        assertThatThrownBy(() -> service.requireNotConnected(1L, LinkType.GITHUB))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(IntegrationErrorCode.WORKSPACE_INTEGRATION_LOCKED));

        verify(projectIntegrationRepository, never()).findByProjectIdAndLinkType(1L, LinkType.GITHUB);
    }
}
