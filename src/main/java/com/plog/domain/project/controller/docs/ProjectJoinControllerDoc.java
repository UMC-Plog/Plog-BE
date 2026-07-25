package com.plog.domain.project.controller.docs;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project", description = "프로젝트 API")
public interface ProjectJoinControllerDoc {

    @Operation(
            summary = "초대 코드로 프로젝트 참여",
            description = """
                    사용자가 유효한 초대 코드로 프로젝트에 MEMBER/ACTIVE 상태로 참여합니다.
                    프론트의 초대 수락 화면에서 수락 버튼을 누를 때 호출합니다.
                    이미 참여 중인 프로젝트면 409를 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "프로젝트 참여 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "초대 코드 누락 또는 유효하지 않은 초대 코드",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 참여 중인 프로젝트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<ProjectJoinResponse>> joinProject(
            Long userId,
            ProjectJoinRequest request
    );
}
