package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportGenerationLauncherTest {

    private static final Long USER_ID = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long REPORT_ID = 20L;

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ProjectAccessService projectAccessService;
    @Mock
    private ReportGenerationService reportGenerationService;

    @InjectMocks
    private ReportGenerationLauncher launcher;

    @Test
    void dispatchesGenerationForAGeneratingReport() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(generatingReport()));

        launcher.launch(USER_ID, REPORT_ID);

        verify(projectAccessService).requireOwner(PROJECT_ID, USER_ID);
        verify(reportGenerationService).generateAsync(REPORT_ID);
    }

    // 검증을 비동기 안으로 넣으면 잘못된 요청도 202 를 받고 조용히 실패한다.
    @Test
    void rejectsNonOwnerBeforeDispatching() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(generatingReport()));
        when(projectAccessService.requireOwner(PROJECT_ID, USER_ID))
                .thenThrow(new ApiException(ProjectErrorCode.PROJECT_SETTING_PERMISSION_DENIED));

        assertThatThrownBy(() -> launcher.launch(USER_ID, REPORT_ID))
                .isInstanceOf(ApiException.class);
        verify(reportGenerationService, never()).generateAsync(anyLong());
    }

    @Test
    void rejectsAlreadyPublishedReportBeforeDispatching() {
        Report published = generatingReport();
        published.complete(LocalDateTime.of(2026, 8, 5, 10, 0));
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> launcher.launch(USER_ID, REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        verify(reportGenerationService, never()).generateAsync(anyLong());
    }

    @Test
    void rejectsMissingReport() {
        when(reportRepository.findWithProjectById(REPORT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> launcher.launch(USER_ID, REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ReportErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    void rejectsMissingPrincipal() {
        assertThatThrownBy(() -> launcher.launch(null, REPORT_ID))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        verify(reportRepository, never()).findWithProjectById(anyLong());
    }

    private Report generatingReport() {
        Report report = Report.start(Project.builder()
                .id(PROJECT_ID)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted")
                .build());
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        return report;
    }
}
