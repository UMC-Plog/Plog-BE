package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.IntegrationCollectionStatus;
import com.plog.domain.integration.entity.IntegrationConnectionStatus;
import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "provider별 외부 연동 상태")
public record IntegrationItemResponse(
        @Schema(description = "외부 provider 유형", example = "GITHUB",
                allowableValues = {"GITHUB", "FIGMA", "NOTION", "GOOGLE"})
        LinkType linkType,
        @Schema(description = "해당 provider가 프로젝트에 연결되어 있으면 true", example = "true")
        boolean linked,
        @Schema(description = "화면 표시용 연결 계정/워크스페이스 이름. 미연결이면 null", example = "UMC-Plog")
        String connectedAccountName,
        @Schema(description = "프로젝트 provider 연결 상태",
                allowableValues = {"ACTIVE", "REAUTH_REQUIRED", "REVOKED"}, example = "ACTIVE")
        IntegrationConnectionStatus connectionStatus,
        @Schema(description = "사용자가 provider를 다시 연동해야 하면 true", example = "false")
        boolean reauthorizationRequired,
        @Schema(description = "해당 provider에 등록된 리소스들의 최근 수집 상태",
                allowableValues = {"NOT_STARTED", "PENDING", "RUNNING", "RETRYING", "SUCCEEDED",
                        "PARTIAL_FAILED", "FAILED", "REAUTH_REQUIRED"}, example = "SUCCEEDED")
        IntegrationCollectionStatus collectionStatus,
        @Schema(description = "해당 provider 리소스 중 가장 최근 수집 완료 시각", example = "2026-08-02T12:00:00Z")
        Instant lastCollectedAt,
        @Schema(description = "가장 최근 수집 실패 원인. 실패가 없으면 null",
                example = "provider temporarily unavailable")
        String lastCollectionFailure
) {
    public IntegrationItemResponse(LinkType linkType, boolean linked, String connectedAccountName) {
        this(
                linkType,
                linked,
                connectedAccountName,
                linked ? IntegrationConnectionStatus.ACTIVE : null,
                false,
                IntegrationCollectionStatus.NOT_STARTED,
                null,
                null
        );
    }
}
