package com.plog.domain.project.dto.response;

public record ProjectInviteReissueResponse(
        String inviteCode,
        String inviteUrl,
        boolean previousInviteInvalidated
) {

    @Override
    public String toString() {
        return "ProjectInviteReissueResponse[inviteCode=REDACTED, "
                + "inviteUrl=REDACTED, previousInviteInvalidated="
                + previousInviteInvalidated + "]";
    }
}
