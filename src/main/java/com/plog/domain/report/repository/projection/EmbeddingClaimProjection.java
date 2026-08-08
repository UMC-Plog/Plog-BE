package com.plog.domain.report.repository.projection;

import com.plog.domain.report.repository.ReportActivityLogRepository;

/**
 * {@link ReportActivityLogRepository #selectClaimableEmbeddingActivities}의 결과 투영.
 * id/content만 있으면 임베딩 배치가 처리하기에 충분해서, 엔티티 전체를 안 불러온다.
 */
public interface EmbeddingClaimProjection {
    Long getId();

    String getContent();
}