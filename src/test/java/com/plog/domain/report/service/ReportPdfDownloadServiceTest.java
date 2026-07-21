package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.dto.response.ReportPdfDownloadResponse;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageDto;
import com.plog.infrastructure.s3.FileStorageErrorCode;
import com.plog.infrastructure.s3.FileStorageService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportPdfDownloadServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private Report report;

    @Mock
    private Project project;

    private ReportPdfDownloadService service;

    @BeforeEach
    void setUp() {
        service = new ReportPdfDownloadService(
                reportRepository,
                projectAccessService,
                fileStorageService
        );
    }

    @Test
    void createsAThreeHundredSecondUrlForAnAccessibleCompletedReport() {
        given(reportRepository.findById(20L)).willReturn(Optional.of(report));
        given(report.getProject()).willReturn(project);
        given(project.getId()).willReturn(10L);
        given(report.getStatus()).willReturn(ReportStatus.COMPLETED);
        given(report.getPdfObjectKey()).willReturn("reports/10/report.pdf");
        given(report.getPdfFileName()).willReturn("Plog-report.pdf");
        given(fileStorageService.createDownloadUrl(
                "reports/10/report.pdf",
                Duration.ofSeconds(300)
        )).willReturn(new FileStorageDto.PresignedDownloadResponse(
                "https://storage.test/report.pdf",
                300
        ));

        ReportPdfDownloadResponse response = service.createDownloadUrl(1L, 20L);

        verify(projectAccessService).requireActiveMember(10L, 1L);
        assertThat(response).isEqualTo(new ReportPdfDownloadResponse(
                20L,
                "Plog-report.pdf",
                "https://storage.test/report.pdf",
                300
        ));
    }

    @Test
    void rejectsAMissingReport() {
        given(reportRepository.findById(20L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_NOT_FOUND));

        verifyNoInteractions(projectAccessService, fileStorageService);
    }

    @Test
    void rejectsAUserWithoutAnActiveProjectMembership() {
        given(reportRepository.findById(20L)).willReturn(Optional.of(report));
        given(report.getProject()).willReturn(project);
        given(project.getId()).willReturn(10L);
        given(projectAccessService.requireActiveMember(10L, 1L))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void rejectsAReportThatIsNotCompleted() {
        givenAccessibleReport();
        given(report.getStatus()).willReturn(ReportStatus.GENERATING);

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.REPORT_NOT_COMPLETED));

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void rejectsACompletedReportWithoutPdfMetadata() {
        givenAccessibleReport();
        given(report.getStatus()).willReturn(ReportStatus.COMPLETED);
        given(report.getPdfObjectKey()).willReturn(null);

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ReportErrorCode.REPORT_PDF_NOT_FOUND));

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void propagatesAStorageDisabledError() {
        givenAccessibleReport();
        given(report.getStatus()).willReturn(ReportStatus.COMPLETED);
        given(report.getPdfObjectKey()).willReturn("reports/10/report.pdf");
        given(report.getPdfFileName()).willReturn("Plog-report.pdf");
        given(fileStorageService.createDownloadUrl(
                "reports/10/report.pdf",
                Duration.ofSeconds(300)
        )).willThrow(new ApiException(FileStorageErrorCode.FILE_STORAGE_DISABLED));

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 20L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.FILE_STORAGE_DISABLED));
    }

    private void givenAccessibleReport() {
        given(reportRepository.findById(20L)).willReturn(Optional.of(report));
        given(report.getProject()).willReturn(project);
        given(project.getId()).willReturn(10L);
    }
}
