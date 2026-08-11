package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportActivityPreparationServiceTest {

    @Mock
    private ReportActivityLogRepository repository;
    @Mock
    private ActivityRefinementService refinementService;
    @Mock
    private ActivityEmbeddingService embeddingService;
    @Mock
    private ActivityClassificationService classificationService;
    @InjectMocks
    private ReportActivityPreparationService service;

    @Test
    void returnsImmediatelyWhenSnapshotHasNoPendingActivity() {
        LocalDateTime snapshotAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        when(repository.countPendingReportActivities(eq(1L), eq(snapshotAt), any())).thenReturn(0L);

        service.prepare(1L, snapshotAt);

        verify(refinementService, never()).refineNoiseBatch();
    }

    @Test
    void rejectsReportWhenPendingActivityCannotProgress() {
        LocalDateTime snapshotAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        when(repository.countPendingReportActivities(eq(1L), eq(snapshotAt), any())).thenReturn(1L);

        assertThatThrownBy(() -> service.prepare(1L, snapshotAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending=1");
    }
}
