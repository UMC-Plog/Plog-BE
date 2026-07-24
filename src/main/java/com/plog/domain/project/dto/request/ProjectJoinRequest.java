package com.plog.domain.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "초대 코드 프로젝트 참여 요청")
public record ProjectJoinRequest(
        @NotBlank(message = "초대 코드는 필수입니다.")
        @Schema(description = "초대 링크 또는 초대 미리보기에서 전달받은 초대 코드", example = "abc123")
        String inviteCode
) {

    @Override
    public String toString() {
        return "ProjectJoinRequest[inviteCode=***]";
    }
}
