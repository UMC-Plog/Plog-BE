package com.plog.domain.report.scheduler;

import com.plog.domain.report.config.ReportAsyncConfig;
import com.plog.domain.report.service.ReportPdfRecoveryService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 배포 전 실패했거나 일시 장애로 누락된 PDF를 기존 리포트 내용으로 복구한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "plog.report.scheduler.enabled", havingValue = "true")
public class ReportPdfRecoveryScheduler {

    private final ReportPdfRecoveryService recoveryService;
    private final AtomicBoolean running = new AtomicBoolean();

    @Async(ReportAsyncConfig.REPORT_EXECUTOR)
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
    }

    @Scheduled(
            fixedDelayString = "${plog.report.pdf.recovery-delay-ms}",
            initialDelayString = "${plog.report.pdf.recovery-delay-ms}"
    )
    public void recover() {
        if (!running.compareAndSet(false, true)) {
            log.info("누락 리포트 PDF 복구가 이미 실행 중이라 이번 호출을 건너뜁니다.");
            return;
        }
        try {
            int recovered = recoveryService.recoverMissingArchives();
            if (recovered > 0) {
                log.info("누락 리포트 PDF 복구 완료: count={}", recovered);
            }
        } catch (RuntimeException exception) {
            log.error("누락 리포트 PDF 복구 배치 실패", exception);
        } finally {
            running.set(false);
        }
    }
}
