package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectJoinResponse(
        Long projectId,
        String projectName,
        Long projectMemberId,
        ProjectRole role,
        ProjectStatus projectStatus,
        MemberStatus memberStatus,
        LocalDateTime joinedAt
) {
}
