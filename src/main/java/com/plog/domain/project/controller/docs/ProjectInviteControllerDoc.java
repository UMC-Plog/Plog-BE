package com.plog.domain.project.controller.docs;

import com.plog.domain.project.dto.response.ProjectInviteReissueResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project", description = "프로젝트 API")
public interface ProjectInviteControllerDoc {

    @Operation(
            summary = "프로젝트 초대 코드 발급",
            description = """
                    ACTIVE OWNER가 새 초대 코드를 발급합니다.
                    이미 사용 중이던 초대 코드가 있으면 즉시 무효화하고, previousInviteInvalidated=true로 내려줍니다.
                    프론트는 inviteUrl을 복사 버튼/공유 버튼에 사용할 수 있고, inviteCode는 직접 입력 참여 플로우에 사용할 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "초대 코드 발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE OWNER 권한 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "초대 토큰 발급 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<ProjectInviteReissueResponse>> reissueInvite(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            Long userId
    );
}
