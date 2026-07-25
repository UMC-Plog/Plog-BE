package com.plog.domain.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로젝트 초대 코드 발급 응답")
public record ProjectInviteReissueResponse(
        @Schema(description = "새로 발급된 초대 코드", example = "abc123")
        String inviteCode,
        @Schema(description = "새 초대 코드가 포함된 초대 링크", example = "http://localhost:5173/invite/abc123")
        String inviteUrl,
        @Schema(description = "기존 초대 코드가 있었고 이번 발급으로 즉시 무효화되었으면 true", example = "true")
        boolean previousInviteInvalidated
) {

    @Override
    public String toString() {
        return "ProjectInviteReissueResponse[inviteCode=REDACTED, "
                + "inviteUrl=REDACTED, previousInviteInvalidated="
                + previousInviteInvalidated + "]";
    }
}
