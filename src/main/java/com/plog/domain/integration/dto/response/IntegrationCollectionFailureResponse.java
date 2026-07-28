package com.plog.domain.integration.dto.response;

import com.plog.domain.integration.entity.LinkType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리소스별 수집 실패 정보")
public record IntegrationCollectionFailureResponse(
        @Schema(description = "Plog 외부 연동 리소스 ID", example = "12")
        Long resourceId,
        @Schema(description = "외부 provider", example = "GOOGLE")
        LinkType linkType,
        @Schema(description = "화면에 표시할 리소스 이름", example = "캡스톤 발표자료")
        String resourceName,
        @Schema(description = "수집 실패 원인", example = "provider resource access denied")
        String reason
) {
}
