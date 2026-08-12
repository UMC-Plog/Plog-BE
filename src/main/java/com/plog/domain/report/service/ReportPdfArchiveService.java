package com.plog.domain.report.service;

import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportPdfArchiveService {

    private final ReportRepository reportRepository;
    private final ReportMemberResultRepository resultRepository;
    private final ReportDetailService reportDetailService;
    private final ReportBrowserRenderer renderer;
    private final FileStorageService fileStorageService;

    public void generateAndAttach(Long reportId) {
        Report report = reportRepository.findWithProjectById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        List<ReportMemberResult> members = resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(reportId);
        ReportDetailResponse team = reportDetailService.getReportForRendering(reportId);
        List<ReportMemberResultResponse> personal = reportDetailService.getMemberResultsForRendering(reportId);

        ReportBrowserRenderer.RenderedReports rendered = renderer.render(team, personal);
        List<ArchiveUpload> uploads = new ArrayList<>(members.size());
        for (ReportMemberResult member : members) {
            Long projectMemberId = member.getProjectMember().getId();
            byte[] personalPdf = rendered.personalPdfs().get(projectMemberId);
            if (personalPdf == null) {
                throw new IllegalStateException("개인 리포트 PDF가 누락되었습니다: projectMemberId=" + projectMemberId);
            }
            String fileName = report.getTeamReportCode() + "-reports.zip";
            String objectKey = "reports/" + reportId + "/members/" + projectMemberId + "/" + fileName;
            byte[] archive = zip(rendered.teamPdf(), personalPdf);
            fileStorageService.putGeneratedObject(objectKey, "application/zip", archive);
            uploads.add(new ArchiveUpload(member, objectKey, fileName));
            log.info("사용자별 리포트 PDF ZIP 생성 완료: reportId={}, projectMemberId={}, archiveBytes={}",
                    reportId, projectMemberId, archive.length);
        }
        uploads.forEach(upload -> upload.member().attachPdfArchive(upload.objectKey(), upload.fileName()));
        resultRepository.saveAll(members);
    }

    private byte[] zip(byte[] teamPdf, byte[] personalPdf) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            add(zip, "team-report.pdf", teamPdf);
            add(zip, "personal-report.pdf", personalPdf);
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

    private record ArchiveUpload(ReportMemberResult member, String objectKey, String fileName) {
    }
}
