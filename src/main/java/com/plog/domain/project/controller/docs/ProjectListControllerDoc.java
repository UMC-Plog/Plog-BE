package com.plog.domain.project.controller.docs;

import com.plog.domain.project.dto.response.ProjectListResponse;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
            description = """
                    요청 사용자가 ACTIVE 멤버로 참여 중인 프로젝트를 Slice로 조회합니다.
                    status를 넘기면 IN_PROGRESS 또는 COMPLETED 상태로 필터링합니다.
                    응답에는 대시보드 카드에 필요한 프로젝트명, 유형, 종료일, 진행률, 멤버 미리보기가 포함됩니다.
                    """
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
            @Parameter(
                    description = "프로젝트 상태 필터. IN_PROGRESS=진행 중, COMPLETED=완료. 생략하면 전체 조회",
                    example = "IN_PROGRESS",
                    schema = @Schema(allowableValues = {"IN_PROGRESS", "COMPLETED"})
            )
            ProjectStatus status,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    );
}
