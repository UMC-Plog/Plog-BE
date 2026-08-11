package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
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
    @Mock private ReportPdfRenderer renderer;
    @Mock private FileStorageService fileStorageService;
    @Mock private ReportTextWriter textWriter;
    @InjectMocks private ReportPdfArchiveService service;

    @Test
    void usesRealNameAndContextSpecificCodesInPdfArchive() throws Exception {
        Project project = Project.builder()
                .id(15L)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .build();
        Report report = Report.start(project);
        ReflectionTestUtils.setField(report, "id", 20L);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 8, 1, 0, 0));
        ProjectMember member = ProjectMember.builder()
                .id(7L)
                .project(project)
                .user(User.createLocal("member@plog.test", "encoded", "김실명", "계정닉네임"))
                .anNickname("프로젝트닉네임")
                .build();
        ReportMemberResult result = ReportMemberResult.create(report, member);
        when(reportRepository.findWithProjectById(20L)).thenReturn(Optional.of(report));
        when(resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(20L)).thenReturn(List.of(result));
        when(renderer.render(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).getBytes(StandardCharsets.UTF_8));

        service.generateAndAttach(20L);

        ArgumentCaptor<byte[]> archive = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).putGeneratedObject(
                org.mockito.ArgumentMatchers.eq("reports/20/PLOG-T-2026-08-15-reports.zip"),
                org.mockito.ArgumentMatchers.eq("application/zip"), archive.capture());
        verify(textWriter).attachPdfArchive(
                20L, "reports/20/PLOG-T-2026-08-15-reports.zip", "PLOG-T-2026-08-15-reports.zip");

        Map<String, String> files = unzip(archive.getValue());
        assertThat(files.get("team-report.pdf"))
                .contains("김실명", "PLOG-T-2026-08-15")
                .doesNotContain("프로젝트닉네임", "계정닉네임");
        assertThat(files.get("member-7-report.pdf"))
                .contains("김실명", "PLOG-P-2026-08-15")
                .doesNotContain("프로젝트닉네임", "계정닉네임");
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
