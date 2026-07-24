package com.plog.domain.project.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.plog.domain.project.entity.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "프로젝트 생성 요청")
public record ProjectCreateRequest(
        @Schema(description = "프로젝트 이름. 앞뒤 공백은 서버에서 정리하며, 너무 짧거나 빈 값이면 검증 오류입니다.",
                example = "Plog")
        String projectName,
        @NotNull(message = "프로젝트 유형은 필수입니다.")
        @JsonDeserialize(using = StrictProjectTypeDeserializer.class)
        @Schema(description = "프로젝트 유형. DEVELOP=개발 프로젝트, GENERAL=일반 프로젝트",
                example = "DEVELOP", allowableValues = {"DEVELOP", "GENERAL"})
        ProjectType projectType,
        @Schema(description = "예상 종료일. 오늘 이후 날짜만 허용됩니다.", example = "2026-07-31")
        LocalDate endDay
) {
}
