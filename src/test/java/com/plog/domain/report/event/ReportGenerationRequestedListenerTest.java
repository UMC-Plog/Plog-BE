package com.plog.domain.report.event;

import static org.mockito.Mockito.verify;

import com.plog.domain.report.service.ReportGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportGenerationRequestedListenerTest {

    @Mock
    private ReportGenerationService generationService;

    @InjectMocks
    private ReportGenerationRequestedListener listener;

    @Test
    void startsGenerationForCommittedReport() {
        listener.onRequested(new ReportGenerationRequestedEvent(15L));

        verify(generationService).generateAsync(15L);
    }
}
