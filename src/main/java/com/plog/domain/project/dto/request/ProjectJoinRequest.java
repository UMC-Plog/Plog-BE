package com.plog.domain.project.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProjectJoinRequest(
        @NotBlank(message = "초대 코드는 필수입니다.")
        String inviteCode
) {

    @Override
    public String toString() {
        return "ProjectJoinRequest[inviteCode=***]";
    }
}
