package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.IntegrationCollectionJobStatus;
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
        @Schema(description = "레거시 최종 수집 run 상태. 현재 수동 수집의 폴링 기준이 아니며, run이 없으면 null. "
                + "수동 수집 진행 상태는 collectionJobStatus를 사용합니다.",
                allowableValues = {"PENDING", "RUNNING", "RETRYABLE", "SUCCEEDED", "PARTIAL_FAILED"})
        IntegrationCollectionRunStatus finalCollectionStatus,
        @Schema(description = "최종 수집의 최근 실패 요약. 실패가 없으면 null")
        String finalCollectionFailure,
        @Schema(description = "가장 최근 수동 수집 잡의 상태. 요청한 적이 없으면 null. "
                + "PENDING·RUNNING·RETRYABLE은 진행 상태이므로 폴링을 계속하고, "
                + "SUCCEEDED·PARTIAL_FAILED·FAILED는 종료 상태이므로 폴링을 종료합니다.",
                allowableValues = {
                        "PENDING", "RUNNING", "RETRYABLE", "SUCCEEDED", "PARTIAL_FAILED", "FAILED"})
        IntegrationCollectionJobStatus collectionJobStatus,
        @Schema(description = "가장 최근 수집 잡의 실패 요약. 실패가 없으면 null")
        String collectionJobFailure,
        @Schema(description = "가장 최근 수집 잡이 시도한 리소스 수. 아직 끝나지 않았으면 null")
        Integer requestedResourceCount,
        @Schema(description = "가장 최근 수집 잡이 성공한 리소스 수. 아직 끝나지 않았으면 null")
        Integer collectedResourceCount
) {
    public IntegrationStatusResponse(
            Long projectId,
            Long projectMemberId,
            List<IntegrationItemResponse> integrations
    ) {
        this(projectId, projectMemberId, integrations, null, null, null, null, null, null);
    }
}
