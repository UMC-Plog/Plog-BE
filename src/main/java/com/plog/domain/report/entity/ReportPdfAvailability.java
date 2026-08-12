package com.plog.domain.report.entity;

/**
 * "PDF ZIP 다운로드 URL 발급이 성공할 수 있는 리포트인가"의 단일 기준.
 * <p>
 * 리포트 완료(COMPLETED)와 ZIP 존재 여부는 별개다 — 발행은 됐지만 아카이브 업로드가 실패해
 * PDF 메타데이터가 비어 있는 리포트가 있을 수 있다. 그래서 프론트는 status 가 아니라 이 값으로
 * 다운로드 버튼을 그린다.
 * <p>
 * 상세/목록 응답의 pdfAvailable 과 {@code ReportPdfDownloadService} 의 통과 조건이 갈리면
 * 버튼은 활성인데 요청은 409/404 로 떨어진다. 조건을 바꿀 일이 생기면 여기 한 곳만 고친다.
 */
public final class ReportPdfAvailability {

    private ReportPdfAvailability() {
    }

    public static boolean isAvailable(ReportStatus status, String pdfObjectKey, String pdfFileName) {
        return status != null && status.isPublished()
                && hasText(pdfObjectKey)
                && hasText(pdfFileName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
