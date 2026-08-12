package com.plog.domain.report.service;

import com.plog.domain.report.repository.ReportMemberResultRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 발행된 본문은 유지하고 누락된 PDF ZIP만 복구한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPdfRecoveryService {

    private static final int BATCH_SIZE = 5;

    private final ReportMemberResultRepository resultRepository;
    private final ReportPdfArchiveService pdfArchiveService;

    public int recoverMissingArchives() {
        long afterReportId = 0L;
        int recovered = 0;

        while (true) {
            List<Long> reportIds = resultRepository.findCompletedReportIdsMissingPdfAfter(
                    afterReportId,
                    PageRequest.of(0, BATCH_SIZE)
            );
            if (reportIds.isEmpty()) {
                return recovered;
            }

            for (Long reportId : reportIds) {
                try {
                    // 기존 DB의 팀/개인 결과만 렌더링하며 점수 계산이나 LLM 호출은 다시 하지 않는다.
                    pdfArchiveService.generateAndAttach(reportId);
                    recovered++;
                } catch (RuntimeException exception) {
                    log.error("누락 리포트 PDF 복구 실패: reportId={}", reportId, exception);
                }
            }
            // 실패한 ID도 건너뛰어 이번 실행이 같은 페이지를 무한 반복하지 않게 한다.
            afterReportId = reportIds.get(reportIds.size() - 1);
        }
    }
}
