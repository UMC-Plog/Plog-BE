package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.IntegrationCollectionRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "프로젝트 외부 provider 연동 상태 응답")
public record IntegrationStatusResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "요청 사용자의 프로젝트 멤버 ID", example = "100")
        Long projectMemberId,
        @Schema(description = "provider별 연동 상태 목록. GITHUB, FIGMA, NOTION, GOOGLE 순서로 반환됩니다.")
        List<IntegrationItemResponse> integrations,
        @Schema(description = "프로젝트 완료 시 실행되는 최종 외부 데이터 수집 상태. 실행 전이면 null",
                allowableValues = {"PENDING", "RUNNING", "RETRYABLE", "SUCCEEDED", "PARTIAL_FAILED"})
        IntegrationCollectionRunStatus finalCollectionStatus,
        @Schema(description = "최종 수집의 최근 실패 요약. 실패가 없으면 null")
        String finalCollectionFailure
) {
    public IntegrationStatusResponse(
            Long projectId,
            Long projectMemberId,
            List<IntegrationItemResponse> integrations
    ) {
        this(projectId, projectMemberId, integrations, null, null);
    }
}
