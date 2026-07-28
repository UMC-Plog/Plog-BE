package com.plog.domain.integration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "수동 외부 연동 데이터 수집 결과")
public record IntegrationCollectionResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "수집을 시도한 ACTIVE 리소스 수", example = "4")
        int requestedResourceCount,
        @Schema(description = "수집에 성공한 리소스 수", example = "3")
        int collectedResourceCount,
        @Schema(description = "리소스별 수집 실패 정보. 모두 성공하면 빈 배열")
        List<IntegrationCollectionFailureResponse> failures
) {
}
