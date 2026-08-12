package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                org.mockito.ArgumentMatchers.eq("reports/20/PLOG-T-2026-08-00000015-reports.zip"),
                org.mockito.ArgumentMatchers.eq("application/zip"), archive.capture());
        verify(textWriter).attachPdfArchive(
                20L, "reports/20/PLOG-T-2026-08-00000015-reports.zip", "PLOG-T-2026-08-00000015-reports.zip");

        Map<String, String> files = unzip(archive.getValue());
        assertThat(files.get("team-report.pdf"))
                .contains("김실명", "PLOG-T-2026-08-00000015")
                .doesNotContain("프로젝트닉네임", "계정닉네임");
        assertThat(files.get("member-7-report.pdf"))
                .contains("김실명", "PLOG-P-2026-08-00000015")
                .doesNotContain("프로젝트닉네임", "계정닉네임");
    }

    @Test
    void includesScreenSectionsAndDetailedMemberDataInPdfHtml() throws Exception {
        Project project = Project.builder()
                .id(15L)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .startDay(java.time.LocalDate.of(2026, 5, 1))
                .endDay(java.time.LocalDate.of(2026, 6, 12))
                .build();
        Report report = Report.start(project);
        ReflectionTestUtils.setField(report, "id", 20L);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 8, 1, 0, 0));
        ReflectionTestUtils.setField(report, "completedAt", LocalDateTime.of(2026, 8, 12, 18, 0));
        ReflectionTestUtils.setField(report, "teamStrength", "협업 흐름이 안정적입니다.");
        ReflectionTestUtils.setField(report, "teamSuggestion", "진행 상황을 정기적으로 공유해 보세요.");
        ReflectionTestUtils.setField(report, "teamCompletionRate", new java.math.BigDecimal("75.00"));
        ReflectionTestUtils.setField(report, "teamDeadlineComplianceRate", new java.math.BigDecimal("80.00"));

        ProjectMember projectMember = ProjectMember.builder()
                .id(7L)
                .project(project)
                .user(User.createLocal("member@plog.test", "encoded", "김실명", "계정닉네임"))
                .anNickname("프로젝트닉네임")
                .build();
        ReportMemberResult member = ReportMemberResult.create(report, projectMember);
        ReflectionTestUtils.setField(member, "finalScore", new java.math.BigDecimal("82.50"));
        ReflectionTestUtils.setField(member, "contributionRate", new java.math.BigDecimal("100.00"));
        ReflectionTestUtils.setField(member, "collaborationStability", new java.math.BigDecimal("86.00"));
        ReflectionTestUtils.setField(member, "vulnerability", new java.math.BigDecimal("24.00"));
        ReflectionTestUtils.setField(member, "headline", "팀의 방향을 안정적으로 이끌었어요.");
        ReflectionTestUtils.setField(member, "teamMemberHeadline", "소통과 일정 조율에 적극적으로 참여했어요.");
        ReflectionTestUtils.setField(member, "strengths", "[{\"title\":\"일정 조율\",\"description\":\"일정 변경을 빠르게 공유했어요.\"}]");
        ReflectionTestUtils.setField(member, "weakness", "{\"title\":\"문서화\",\"suggestions\":[\"결정 사항을 기록해 보세요.\"]}");
        ReflectionTestUtils.setField(member, "growth", "{\"growthPoint\":\"기록 습관\",\"keepStrength\":\"협업 태도\",\"nextAction\":\"회의록 작성\"}");
        ReflectionTestUtils.setField(member, "writing", "{\"coverLetter\":\"팀 일정을 조율했습니다.\",\"portfolio\":\"협업 프로세스를 개선했습니다.\"}");
        ReflectionTestUtils.setField(member, "competencyScores", Map.of(
                com.plog.domain.report.entity.CompetencyCategory.COLLABORATION,
                new java.math.BigDecimal("4.50")));
        ReflectionTestUtils.setField(member, "peerKeywords", List.of("책임감", "소통"));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        when(reportRepository.findWithProjectById(20L)).thenReturn(Optional.of(report));
        when(resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(20L)).thenReturn(List.of(member));
        when(renderer.render(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).getBytes(StandardCharsets.UTF_8));

        service.generateAndAttach(20L);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(renderer, times(2)).render(html.capture());
        String teamHtml = html.getAllValues().get(0);
        String memberHtml = html.getAllValues().get(1);
        assertThat(teamHtml).contains("팀 업무 완수 현황", "팀 기여도 분포", "팀원별 활동 요약", "책임감");
        assertThat(memberHtml).contains("기여도 상세 분석", "강점 분석", "취약점 진단",
                "AI 개인 성장 인사이트", "AI 문장 변환", "일정 조율", "문서화", "회의록 작성",
                "PLOG-P-2026-08-00000015");
    }

    @Test
    void preservesUnavailableMetricsAndNormalizesNullableJson() {
        Project project = Project.builder()
                .id(15L)
                .projectName("Plog")
                .inviteTokenHash("invite-hash")
                .inviteTokenEncrypted("encrypted-invite")
                .build();
        Report report = Report.start(project);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 8, 1, 0, 0));
        ProjectMember projectMember = ProjectMember.builder()
                .id(7L)
                .project(project)
                .user(User.createLocal("member@plog.test", "encoded", "김실명", "계정닉네임"))
                .anNickname("프로젝트닉네임")
                .build();
        ReportMemberResult member = ReportMemberResult.create(report, projectMember);
        ReflectionTestUtils.setField(member, "strengths", "null");
        ReflectionTestUtils.setField(member, "weakness",
                "{\"title\":\"문서화\",\"suggestions\":[null,\"결정 사항을 기록해 보세요.\"]}");
        ReflectionTestUtils.setField(member, "competencyScores", Map.of());
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        String teamHtml = ReflectionTestUtils.invokeMethod(service, "teamHtml", report, List.of(member));
        String memberHtml = ReflectionTestUtils.invokeMethod(service, "memberHtml", report, member);

        assertThat(teamHtml)
                .contains("측정 불가")
                .doesNotContain(">0%</td>");
        assertThat(memberHtml)
                .contains("협업 태도", "측정 불가", "문서화", "결정 사항을 기록해 보세요.")
                .doesNotContain("강점 분석", ">0%</div>");
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
