package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import java.time.LocalDate;

public record ProjectCreateResponse(
        Long projectId,
        String projectName,
        ProjectType projectType,
        ProjectStatus status,
        LocalDate startDay,
        LocalDate endDay,
        Long myProjectMemberId,
        ProjectRole myRole,
        Invite invite
) {
    public record Invite(
            String inviteCode,
            String inviteUrl
    ) {
        @Override
        public String toString() {
            return "Invite[inviteCode=[REDACTED], inviteUrl=[REDACTED]]";
        }
    }
}
