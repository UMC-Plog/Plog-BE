package com.plog.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import com.plog.domain.report.config.ReportPdfProperties;
import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportBrowserRenderer {

    private static final String RENDER_DATA_KEY = "__PLOG_REPORT_RENDER_DATA__";
    private static final Semaphore RENDER_SLOT = new Semaphore(1);

    private final ReportPdfProperties properties;
    private final ObjectMapper objectMapper;

    public RenderedReports render(
            ReportDetailResponse team,
            List<ReportMemberResultResponse> members
    ) {
        acquireSlot();
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setExecutablePath(Path.of(properties.chromiumExecutable()))
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage"));
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                byte[] teamPdf = renderPage(browser, "/internal/report-render/team", team);
                Map<Long, byte[]> personalPdfs = new LinkedHashMap<>();
                for (ReportMemberResultResponse member : members) {
                    personalPdfs.put(member.projectMemberId(),
                            renderPage(browser, "/internal/report-render/personal", member));
                }
                return new RenderedReports(teamPdf, Map.copyOf(personalPdfs));
            }
        } finally {
            RENDER_SLOT.release();
        }
    }

    private byte[] renderPage(Browser browser, String path, Object renderData) {
        try (Page page = browser.newPage()) {
            page.addInitScript("window." + RENDER_DATA_KEY + " = " + toJson(renderData));
            double timeoutMillis = properties.timeout().toMillis();
            page.navigate(url(path), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(timeoutMillis));
            page.waitForSelector("[data-report-ready=\"true\"]",
                    new Page.WaitForSelectorOptions().setTimeout(timeoutMillis));
            page.evaluate("""
                    async timeoutMillis => {
                      let timeoutId;
                      try {
                        const resourcesReady = Promise.all([
                          document.fonts.ready,
                          ...Array.from(document.images).map(image => image.complete
                            ? Promise.resolve()
                            : new Promise(resolve => {
                                image.onload = resolve;
                                image.onerror = resolve;
                              }))
                        ]);
                        const timeout = new Promise((_, reject) => {
                          timeoutId = setTimeout(
                            () => reject(new Error('Report assets did not load before timeout')),
                            timeoutMillis
                          );
                        });
                        await Promise.race([resourcesReady, timeout]);
                      } finally {
                        clearTimeout(timeoutId);
                      }
                    }
                    """, timeoutMillis);
            return page.pdf(new Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setPreferCSSPageSize(true));
        }
    }

    private String url(String path) {
        return properties.renderBaseUrl().replaceAll("/+$", "") + path;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("리포트 렌더링 데이터 직렬화에 실패했습니다.", exception);
        }
    }

    private void acquireSlot() {
        try {
            RENDER_SLOT.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("리포트 PDF 렌더링 대기가 중단되었습니다.", exception);
        }
    }

    public record RenderedReports(byte[] teamPdf, Map<Long, byte[]> personalPdfs) {
    }
}
