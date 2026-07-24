package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "프로젝트 생성 응답")
public record ProjectCreateResponse(
        @Schema(description = "생성된 프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "프로젝트 이름", example = "Plog")
        String projectName,
        @Schema(description = "프로젝트 유형", example = "DEVELOP", allowableValues = {"DEVELOP", "GENERAL"})
        ProjectType projectType,
        @Schema(description = "프로젝트 진행 상태", example = "IN_PROGRESS", allowableValues = {"IN_PROGRESS", "COMPLETED"})
        ProjectStatus status,
        @Schema(description = "프로젝트 시작일", example = "2026-07-24")
        LocalDate startDay,
        @Schema(description = "예상 종료일", example = "2026-07-31")
        LocalDate endDay,
        @Schema(description = "요청 사용자의 프로젝트 멤버 ID", example = "100")
        Long myProjectMemberId,
        @Schema(description = "요청 사용자의 프로젝트 역할", example = "OWNER", allowableValues = {"OWNER", "MEMBER"})
        ProjectRole myRole,
        @Schema(description = "프로젝트 초대 정보")
        Invite invite
) {
    @Schema(description = "프로젝트 초대 정보")
    public record Invite(
            @Schema(description = "직접 입력 참여에 사용할 초대 코드", example = "abc123")
            String inviteCode,
            @Schema(description = "프론트 초대 화면으로 이동하는 초대 링크", example = "http://localhost:5173/invite/abc123")
            String inviteUrl
    ) {
        @Override
        public String toString() {
            return "Invite[inviteCode=[REDACTED], inviteUrl=[REDACTED]]";
        }
    }
}
