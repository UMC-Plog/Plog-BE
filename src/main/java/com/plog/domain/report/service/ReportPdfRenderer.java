package com.plog.domain.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportPdfRenderer {

    @Value("${plog.report.pdf.font-path:}")
    private String fontPath;

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
