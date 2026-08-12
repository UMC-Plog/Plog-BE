package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.user.entity.User;
import com.plog.infrastructure.s3.FileStorageService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportPdfArchiveServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ReportMemberResultRepository resultRepository;
    @Mock private ReportDetailService reportDetailService;
    @Mock private ReportBrowserRenderer renderer;
    @Mock private FileStorageService fileStorageService;
    @Mock private ReportDetailResponse teamResponse;
    @Mock private ReportMemberResultResponse firstResponse;
    @Mock private ReportMemberResultResponse secondResponse;
    @InjectMocks private ReportPdfArchiveService service;

    @Test
    void createsOnePrivateTwoFileArchivePerMember() throws Exception {
        Project project = Project.builder()
                .id(15L).projectName("Plog")
                .inviteTokenHash("invite-hash").inviteTokenEncrypted("encrypted-invite")
                .build();
        Report report = Report.start(project);
        ReflectionTestUtils.setField(report, "id", 20L);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 8, 1, 0, 0));
        ReportMemberResult first = member(report, project, 7L, "첫째");
        ReportMemberResult second = member(report, project, 8L, "둘째");

        when(reportRepository.findWithProjectById(20L)).thenReturn(Optional.of(report));
        when(resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(20L))
                .thenReturn(List.of(first, second));
        when(reportDetailService.getReportForRendering(20L)).thenReturn(teamResponse);
        when(reportDetailService.getMemberResultsForRendering(20L))
                .thenReturn(List.of(firstResponse, secondResponse));
        when(renderer.render(teamResponse, List.of(firstResponse, secondResponse)))
                .thenReturn(new ReportBrowserRenderer.RenderedReports(
                        "team".getBytes(StandardCharsets.UTF_8),
                        Map.of(
                                7L, "personal-7".getBytes(StandardCharsets.UTF_8),
                                8L, "personal-8".getBytes(StandardCharsets.UTF_8))));

        service.generateAndAttach(20L);

        ArgumentCaptor<byte[]> firstArchive = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> secondArchive = ArgumentCaptor.forClass(byte[].class);
        String fileName = "PLOG-T-2026-08-00000015-reports.zip";
        verify(fileStorageService).putGeneratedObject(
                eq("reports/20/members/7/" + fileName), eq("application/zip"), firstArchive.capture());
        verify(fileStorageService).putGeneratedObject(
                eq("reports/20/members/8/" + fileName), eq("application/zip"), secondArchive.capture());

        assertThat(unzip(firstArchive.getValue()))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "team-report.pdf", "team",
                        "personal-report.pdf", "personal-7"));
        assertThat(unzip(secondArchive.getValue()))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "team-report.pdf", "team",
                        "personal-report.pdf", "personal-8"));
        assertThat(first.getPdfObjectKey()).isEqualTo("reports/20/members/7/" + fileName);
        assertThat(second.getPdfObjectKey()).isEqualTo("reports/20/members/8/" + fileName);
        verify(resultRepository).saveAll(List.of(first, second));
    }

    @Test
    void doesNotPublishAnyArchiveMetadataWhenAnUploadFails() {
        Project project = Project.builder()
                .id(15L).projectName("Plog")
                .inviteTokenHash("invite-hash").inviteTokenEncrypted("encrypted-invite")
                .build();
        Report report = Report.start(project);
        ReflectionTestUtils.setField(report, "id", 20L);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 8, 1, 0, 0));
        ReportMemberResult first = member(report, project, 7L, "첫째");
        ReportMemberResult second = member(report, project, 8L, "둘째");

        when(reportRepository.findWithProjectById(20L)).thenReturn(Optional.of(report));
        when(resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(20L))
                .thenReturn(List.of(first, second));
        when(reportDetailService.getReportForRendering(20L)).thenReturn(teamResponse);
        when(reportDetailService.getMemberResultsForRendering(20L))
                .thenReturn(List.of(firstResponse, secondResponse));
        when(renderer.render(teamResponse, List.of(firstResponse, secondResponse)))
                .thenReturn(new ReportBrowserRenderer.RenderedReports(
                        "team".getBytes(StandardCharsets.UTF_8),
                        Map.of(
                                7L, "personal-7".getBytes(StandardCharsets.UTF_8),
                                8L, "personal-8".getBytes(StandardCharsets.UTF_8))));
        doNothing().doThrow(new IllegalStateException("S3 failure"))
                .when(fileStorageService).putGeneratedObject(any(), any(), any());

        assertThatThrownBy(() -> service.generateAndAttach(20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("S3 failure");

        verify(resultRepository, never()).saveAll(any());
        assertThat(first.getPdfObjectKey()).isNull();
        assertThat(second.getPdfObjectKey()).isNull();
    }

    private ReportMemberResult member(Report report, Project project, Long id, String name) {
        ProjectMember member = ProjectMember.builder()
                .id(id).project(project)
                .user(User.createLocal(id + "@plog.test", "encoded", name, name))
                .build();
        return ReportMemberResult.create(report, member);
    }

    private Map<String, String> unzip(byte[] archive) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                files.put(entry.getName(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
