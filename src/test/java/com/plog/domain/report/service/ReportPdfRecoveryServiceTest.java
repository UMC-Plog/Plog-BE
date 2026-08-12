package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.plog.domain.report.repository.ReportMemberResultRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportPdfRecoveryServiceTest {

    @Mock
    private ReportMemberResultRepository resultRepository;
    @Mock
    private ReportPdfArchiveService pdfArchiveService;

    @InjectMocks
    private ReportPdfRecoveryService recoveryService;

    @Test
    void recoversEveryPageAndContinuesAfterOneReportFails() {
        when(resultRepository.findCompletedReportIdsMissingPdfAfter(eq(0L), any()))
                .thenReturn(List.of(1L, 2L));
        when(resultRepository.findCompletedReportIdsMissingPdfAfter(eq(2L), any()))
                .thenReturn(List.of(3L));
        when(resultRepository.findCompletedReportIdsMissingPdfAfter(eq(3L), any()))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("render failed"))
                .when(pdfArchiveService).generateAndAttach(1L);

        int recovered = recoveryService.recoverMissingArchives();

        assertThat(recovered).isEqualTo(2);
        InOrder order = inOrder(pdfArchiveService);
        order.verify(pdfArchiveService).generateAndAttach(1L);
        order.verify(pdfArchiveService).generateAndAttach(2L);
        order.verify(pdfArchiveService).generateAndAttach(3L);
    }
}
