package com.plog.domain.project.dto.response;

import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "프로젝트 목록 항목")
public record ProjectListResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "프로젝트 이름", example = "Plog")
        String projectName,
        @Schema(description = "프로젝트 유형", example = "DEVELOP", allowableValues = {"DEVELOP", "GENERAL"})
        ProjectType projectType,
        @Schema(description = "프로젝트 진행 상태", example = "IN_PROGRESS", allowableValues = {"IN_PROGRESS", "COMPLETED"})
        ProjectStatus status,
        @Schema(description = "예상 종료일", example = "2026-07-31")
        LocalDate endDay,
        @Schema(description = "오늘 기준 남은 일수", example = "7")
        long remainingDays,
        @Schema(description = "프로젝트 ACTIVE 멤버 수", example = "4")
        int memberCount,
        @Schema(description = "목록 카드에 표시할 멤버 미리보기")
        List<MemberPreview> memberPreviews,
        @Schema(description = "미리보기로 표시되지 않은 추가 멤버 수", example = "1")
        int extraMemberCount,
        @Schema(description = "프로젝트 진행률 퍼센트", example = "70")
        int progressPercent
) {

    @Schema(description = "프로젝트 목록 멤버 미리보기")
    public record MemberPreview(
            @Schema(description = "사용자 ID", example = "10")
            Long userId,
            @Schema(description = "사용자 닉네임", example = "바나")
            String nickname,
            @Schema(description = "프로필 프리셋", example = "PROFILE_1")
            ProfilePreset profilePreset
    ) {
    }
}
