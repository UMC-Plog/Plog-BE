package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.project.dto.response.ProjectInviteReissueResponse;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectInviteServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private InviteTokenService inviteTokenService;

    private ProjectInviteService projectInviteService;

    @BeforeEach
    void setUp() {
        projectInviteService = new ProjectInviteService(
                projectRepository,
                projectAccessService,
                inviteTokenService,
                "https://plog.test/invite"
        );
    }

    @Test
    void reissuesTheInviteAfterOwnerAuthorization() {
        given(projectRepository.existsById(10L)).willReturn(true);
        given(inviteTokenService.rotate(10L)).willReturn(new InviteTokenService.IssuedToken(
                "new-code",
                "new-hash",
                "new-encrypted"
        ));

        ProjectInviteReissueResponse response = projectInviteService.reissue(10L, 1L);

        assertThat(response.inviteCode()).isEqualTo("new-code");
        assertThat(response.inviteUrl()).isEqualTo("https://plog.test/invite/new-code");
        assertThat(response.previousInviteInvalidated()).isTrue();
        verify(projectAccessService).requireOwner(10L, 1L);
        verify(inviteTokenService).rotate(10L);
    }

    @Test
    void returnsNotFoundBeforeCheckingMembership() {
        given(projectRepository.existsById(404L)).willReturn(false);

        assertThatThrownBy(() -> projectInviteService.reissue(404L, 1L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND));

        verify(projectAccessService, never()).requireOwner(404L, 1L);
        verify(inviteTokenService, never()).rotate(404L);
    }

    @Test
    void doesNotRotateWhenOwnerAuthorizationFails() {
        given(projectRepository.existsById(10L)).willReturn(true);
        given(projectAccessService.requireOwner(10L, 2L))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED));

        assertThatThrownBy(() -> projectInviteService.reissue(10L, 2L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED));

        verify(inviteTokenService, never()).rotate(10L);
    }
}
