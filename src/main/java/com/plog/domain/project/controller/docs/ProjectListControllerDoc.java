package com.plog.domain.project.controller.docs;

import com.plog.domain.project.dto.response.ProjectListResponse;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project", description = "프로젝트 API")
public interface ProjectListControllerDoc {

    @Operation(
            summary = "내 프로젝트 목록 조회",
            description = "ACTIVE 멤버십의 프로젝트를 상태별로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "프로젝트 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 상태 또는 페이지 조건",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<SliceResponse<ProjectListResponse>>> getProjects(
            Long userId,
            ProjectStatus status,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    );
}
