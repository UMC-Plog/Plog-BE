package com.plog.domain.report.event;

import com.plog.domain.report.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReportGenerationRequestedListener {

    private final ReportGenerationService reportGenerationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportGenerationRequested(ReportGenerationRequestedEvent event) {
        reportGenerationService.generateAsync(event.reportId());
    }
}
