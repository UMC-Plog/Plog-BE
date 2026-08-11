package com.plog.domain.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportPdfRenderer {

    @Value("${plog.report.pdf.font-path:}")
    private String fontPath;

    @PostConstruct
    void validateFont() {
        File font = fontPath == null ? null : new File(fontPath);
        if (font == null || !font.isFile()) {
            log.warn("리포트 PDF 폰트를 찾을 수 없어 시스템 기본 폰트를 사용합니다: fontPath={}", fontPath);
            return;
        }
        log.info("리포트 PDF 폰트 확인 완료: fontPath={}", font.getAbsolutePath());
    }

    public byte[] render(String html) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            File font = fontPath == null ? null : new File(fontPath);
            if (font != null && font.isFile()) {
                builder.useFont(font, "PlogFont");
            }
            builder.withHtmlContent(html, null).toStream(output).run();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("리포트 PDF 생성에 실패했습니다.", exception);
        }
    }
}
