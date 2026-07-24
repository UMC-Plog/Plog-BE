package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "프로젝트 참여 응답")
public record ProjectJoinResponse(
        @Schema(description = "참여한 프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "프로젝트 이름", example = "Plog")
        String projectName,
        @Schema(description = "생성/활성화된 프로젝트 멤버 ID", example = "100")
        Long projectMemberId,
        @Schema(description = "프로젝트 역할", example = "MEMBER", allowableValues = {"OWNER", "MEMBER"})
        ProjectRole role,
        @Schema(description = "프로젝트 진행 상태", example = "IN_PROGRESS", allowableValues = {"IN_PROGRESS", "COMPLETED"})
        ProjectStatus projectStatus,
        @Schema(description = "프로젝트 멤버 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "EXIT"})
        MemberStatus memberStatus,
        @Schema(description = "프로젝트 참여 시각", example = "2026-07-24T13:30:00Z")
        Instant joinedAt
) {
}
