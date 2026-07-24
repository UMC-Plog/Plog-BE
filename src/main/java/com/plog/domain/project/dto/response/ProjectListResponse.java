package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.user.entity.ProfilePreset;
import java.time.LocalDate;
import java.util.List;

public record ProjectListResponse(
        Long projectId,
        String projectName,
        ProjectType projectType,
        ProjectStatus status,
        LocalDate endDay,
        long remainingDays,
        int memberCount,
        List<MemberPreview> memberPreviews,
        int extraMemberCount,
        int progressPercent
) {

    public record MemberPreview(
            Long userId,
            String nickname,
            ProfilePreset profilePreset
    ) {
    }
}
