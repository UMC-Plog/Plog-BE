package com.plog.domain.report.service;

import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportActivityPreparationService {

    private static final int MAX_DRAIN_ROUNDS = 100;
    private static final List<SourceDomain> INTERNAL =
            List.of(SourceDomain.TASK, SourceDomain.CHAT, SourceDomain.POST);
    private final ReportActivityLogRepository repository;
    private final ActivityRefinementService refinementService;
    private final ActivityEmbeddingService embeddingService;
    private final ActivityClassificationService classificationService;

    public void prepare(Long projectId, LocalDateTime snapshotAt) {
        for (int round = 0; round < MAX_DRAIN_ROUNDS; round++) {
            if (pending(projectId, snapshotAt) == 0) {
                return;
            }
            int progressed = refinementService.refineNoiseBatch()
                    + embeddingService.embedBatch()
                    + classificationService.classifyBatch();
            if (progressed == 0) {
                break;
            }
        }
        long remaining = pending(projectId, snapshotAt);
        if (remaining > 0) {
            throw new IllegalStateException("리포트 활동 준비가 끝나지 않았습니다. pending=" + remaining);
        }
    }

    private long pending(Long projectId, LocalDateTime snapshotAt) {
        return repository.countPendingReportActivities(projectId, snapshotAt, INTERNAL);
    }
}
