package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "초대 코드 프로젝트 미리보기 응답")
public record ProjectInvitationPreviewResponse(
        @Schema(description = "초대 대상 프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "초대 대상 프로젝트 이름", example = "Plog")
        String projectName,
        @Schema(description = "프로젝트 유형", example = "GENERAL", allowableValues = {"DEVELOP", "GENERAL"})
        ProjectType projectType,
        @Schema(description = "예상 종료일", example = "2026-07-31")
        LocalDate endDay
) {
}
