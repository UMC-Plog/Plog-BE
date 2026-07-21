package com.plog.domain.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.dto.response.ProjectInvitationPreviewResponse;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectInvitationPreviewServiceTest {

    @Mock
    private InviteTokenService inviteTokenService;

    private ProjectInvitationPreviewService previewService;

    @BeforeEach
    void setUp() {
        previewService = new ProjectInvitationPreviewService(inviteTokenService);
    }

    @Test
    void previewsAProjectWithoutChangingInvitationState() {
        given(inviteTokenService.findProjectByRawToken("valid-code"))
                .willReturn(Optional.of(project()));

        ProjectInvitationPreviewResponse response = previewService.preview(1L, "valid-code");

        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.projectName()).isEqualTo("Plog API");
        assertThat(response.projectType()).isEqualTo(ProjectType.DEVELOP);
        assertThat(response.endDay()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void rejectsAnInvalidInviteCode() {
        given(inviteTokenService.findProjectByRawToken("invalid-code"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> previewService.preview(1L, "invalid-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.INVALID_INVITE_CODE));
    }

    @Test
    void rejectsANullPrincipalBeforeLookingUpTheInviteCode() {
        assertThatThrownBy(() -> previewService.preview(null, "valid-code"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));

        verifyNoInteractions(inviteTokenService);
    }

    private Project project() {
        return Project.builder()
                .id(10L)
                .projectName("Plog API")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 20))
                .endDay(LocalDate.of(2026, 8, 20))
                .build();
    }
}
