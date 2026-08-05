package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "외부 연동 수집 요청 접수 응답")
public record IntegrationCollectionAcceptedResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "수집 잡 ID. 진행 상황은 연동 상태 조회 API로 확인합니다.", example = "42")
        Long jobId,
        @Schema(description = "접수 시점의 잡 상태", example = "PENDING")
        IntegrationCollectionJobStatus status
) {
}
