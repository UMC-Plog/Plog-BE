package com.plog.domain.report.service;

import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportPdfArchiveService {

    private final ReportRepository reportRepository;
    private final ReportMemberResultRepository resultRepository;
    private final ReportPdfRenderer renderer;
    private final FileStorageService fileStorageService;
    private final ReportTextWriter textWriter;

    public void generateAndAttach(Long reportId) {
        Report report = reportRepository.findWithProjectById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        List<ReportMemberResult> members = resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(reportId);
        String code = report.getTeamReportCode();
        String fileName = code + "-reports.zip";
        String objectKey = "reports/" + reportId + "/" + fileName;
        byte[] archive;
        try {
            archive = zip(report, members);
            log.info("리포트 PDF ZIP 렌더링 완료: reportId={}, memberCount={}, archiveBytes={}",
                    reportId, members.size(), archive.length);
        } catch (RuntimeException exception) {
            log.error("리포트 PDF ZIP 렌더링 실패: reportId={}, memberCount={}",
                    reportId, members.size(), exception);
            throw exception;
        }
        try {
            fileStorageService.putGeneratedObject(objectKey, "application/zip", archive);
            log.info("리포트 PDF ZIP S3 업로드 완료: reportId={}, objectKey={}", reportId, objectKey);
        } catch (RuntimeException exception) {
            log.error("리포트 PDF ZIP S3 업로드 실패: reportId={}, objectKey={}",
                    reportId, objectKey, exception);
            throw exception;
        }
        try {
            textWriter.attachPdfArchive(reportId, objectKey, fileName);
            log.info("리포트 PDF ZIP 메타데이터 저장 완료: reportId={}, objectKey={}, fileName={}",
                    reportId, objectKey, fileName);
        } catch (RuntimeException exception) {
            log.error("리포트 PDF ZIP 메타데이터 저장 실패: reportId={}, objectKey={}, fileName={}",
                    reportId, objectKey, fileName, exception);
            throw exception;
        }
    }

    private byte[] zip(Report report, List<ReportMemberResult> members) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            add(zip, "team-report.pdf", renderer.render(teamHtml(report, members)));
            for (ReportMemberResult member : members) {
                add(zip, "member-" + member.getProjectMember().getId() + "-report.pdf",
                        renderer.render(memberHtml(report, member)));
            }
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("리포트 ZIP 생성에 실패했습니다.", exception);
        }
    }

    private void add(ZipOutputStream zip, String name, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String teamHtml(Report report, List<ReportMemberResult> members) {
        StringBuilder rows = new StringBuilder();
        for (ReportMemberResult member : members) {
            rows.append("<tr><td>").append(esc(member.getProjectMember().getUser().getName())).append("</td><td>")
                    .append(value(member.getFinalScore())).append("</td><td>")
                    .append(value(member.getContributionRate())).append("%</td></tr>");
        }
        return page(report.getProject().getProjectName() + " 팀 리포트",
                "<p>리포트 코드: " + esc(report.getTeamReportCode()) + "</p>"
                        + "<p>팀 완료율: " + value(report.getTeamCompletionRate()) + "% / 마감 준수율: "
                        + value(report.getTeamDeadlineComplianceRate()) + "%</p>"
                        + "<h2>AI 인사이트</h2><p>" + esc(report.getTeamStrength()) + "</p><p>"
                        + esc(report.getTeamSuggestion()) + "</p>"
                        + "<table><tr><th>팀원</th><th>종합점수</th><th>기여율</th></tr>" + rows + "</table>");
    }

    private String memberHtml(Report report, ReportMemberResult member) {
        return page(report.getProject().getProjectName() + " - "
                        + member.getProjectMember().getUser().getName() + " 개인 리포트",
                "<p>리포트 코드: " + esc(report.getPersonalReportCode()) + "</p>"
                        + "<h2>" + esc(member.getHeadline()) + "</h2>"
                        + "<p>종합점수: " + value(member.getFinalScore()) + " / 협업 안정도: "
                        + value(member.getCollaborationStability()) + "</p>"
                        + "<p>업무 완료: " + member.getCompletedTaskCount() + "/" + member.getTotalTaskCount()
                        + ", 마감 준수: " + member.getDeadlineMetTaskCount() + "/"
                        + member.getDeadlineTargetTaskCount() + "</p>"
                        + "<p>개선 필요도: " + value(member.getVulnerability()) + "</p>");
    }

    private String page(String title, String body) {
        return "<html><head><meta charset='UTF-8'/><style>"
                + "@page{size:A4;margin:24mm}body{font-family:PlogFont,sans-serif;color:#222}"
                + "h1{color:#1687ee}table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:8px}"
                + "</style></head><body><h1>" + esc(title) + "</h1>" + body + "</body></html>";
    }

    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String value(Object value) {
        return value == null ? "측정 불가" : esc(value.toString());
    }
}
