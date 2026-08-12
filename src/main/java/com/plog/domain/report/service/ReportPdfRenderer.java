package com.plog.domain.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Locale;
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

    /**
     * PDFBox 의 TTFSubsetter 는 glyf 테이블(TrueType 아웃라인)만 다룬다. CFF 아웃라인을 쓰는
     * OTF·폰트 컬렉션을 넘기면 렌더링 시점에 "OTF fonts do not have a glyf table" 로 터진다.
     * <p>
     * 파일 존재 검사로는 절대 못 잡는 실패다 — fonts-noto-cjk 의 NotoSansCJK-Regular.ttc 가
     * 정확히 이 경우였고, 기동 로그는 "폰트 확인 완료"를 찍은 채로 PDF만 전부 실패했다.
     * 모르는 확장자까지 잡으면 거짓 경보가 되므로 확실히 못 쓰는 포맷만 판별한다.
     */
    static boolean isSubsetUnsupported(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerCased = fileName.toLowerCase(Locale.ROOT);
        return lowerCased.endsWith(".otf") || lowerCased.endsWith(".ttc") || lowerCased.endsWith(".otc");
    }

    @PostConstruct
    void validateFont() {
        File font = fontPath == null ? null : new File(fontPath);
        if (font == null || !font.isFile()) {
            log.warn("리포트 PDF 폰트를 찾을 수 없어 시스템 기본 폰트를 사용합니다: fontPath={}", fontPath);
            return;
        }
        if (isSubsetUnsupported(font.getName())) {
            log.error("리포트 PDF 폰트가 서브셋 불가 포맷(CFF 기반 OTF/컬렉션)입니다. "
                    + "이대로 두면 PDF 생성이 매번 실패합니다 — TrueType(.ttf) 폰트로 바꾸세요: fontPath={}",
                    font.getAbsolutePath());
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
