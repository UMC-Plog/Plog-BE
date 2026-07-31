package com.plog.domain.evaluation.dto.response;

import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "평가 대상 팀원")
@Builder
public record TargetMemberDto(
        @Schema(description = "프로젝트 멤버 ID", example = "20")
        Long projectMemberId,
        @Schema(description = "표시 닉네임. 프로젝트 별명이 있으면 별명, 없으면 사용자 닉네임", example = "바나")
        String nickname,
        @Schema(description = "프로필 프리셋(미설정 시 null = 기본 아바타). 이미지 자산은 프론트가 보유하고 서버는 프리셋 코드만 내려준다.",
                example = "OTTER", nullable = true,
                allowableValues = {"OTTER", "PENGUIN", "FROG", "KOALA", "PANDA", "SMILEY", "GHOST", "TIGER"})
        ProfilePreset profilePreset,
        @Schema(description = "내가 이 팀원을 이미 평가했는지 여부", example = "false")
        boolean isEvaluated
) {
}