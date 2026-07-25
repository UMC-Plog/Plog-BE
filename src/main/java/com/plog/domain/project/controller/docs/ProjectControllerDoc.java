package com.plog.domain.project.controller.docs;

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project", description = "프로젝트 API")
public interface ProjectControllerDoc {

    @Operation(
            summary = "프로젝트 생성",
            description = """
                    프로젝트를 생성하고, 생성자를 OWNER/ACTIVE 멤버로 등록합니다.
                    프로젝트 유형은 DEVELOP 또는 GENERAL입니다.
                    응답에는 생성 직후 프론트가 초대 화면에 표시할 inviteCode/inviteUrl도 함께 포함됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "프로젝트 생성 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 프로젝트 이름, 유형 또는 예상 종료일",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    ResponseEntity<ApiResponse<ProjectCreateResponse>> createProject(
            Long userId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = """
                            projectName: 프로젝트 이름
                            projectType: DEVELOP=개발 프로젝트, GENERAL=일반 프로젝트
                            endDay: 예상 종료일
                            """,
                    content = @Content(
                            schema = @Schema(implementation = ProjectCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "projectName": "Plog",
                                      "projectType": "DEVELOP",
                                      "endDay": "2026-07-31"
                                    }
                                    """)
                    )
            )
            ProjectCreateRequest request
    );
}
