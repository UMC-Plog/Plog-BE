package com.plog.domain.project.controller.docs;

import com.plog.domain.project.dto.response.ActiveProjectMemberResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Project", description = "프로젝트, 멤버 및 설정 관리 API")
public interface ProjectMemberControllerDoc {

    @Operation(
            summary = "프로젝트 ACTIVE 멤버 목록 조회",
            description = """
                    프로젝트에 현재 참여 중인 ACTIVE 멤버 목록을 조회합니다.
                    요청자도 해당 프로젝트의 ACTIVE 멤버여야 합니다.
                    프로젝트를 나간 EXIT 상태의 멤버는 조회 결과에서 제외됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "프로젝트 ACTIVE 멤버 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "PROJECT009",
                                      "message": "프로젝트 멤버 목록을 조회했습니다.",
                                      "result": [
                                        {
                                          "projectMemberId": 1,
                                          "nickname": "송민",
                                          "profilePreset": "OTTER"
                                        },
                                        {
                                          "projectMemberId": 3,
                                          "nickname": "정환",
                                          "profilePreset": null
                                        }
                                      ]
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "요청자가 해당 프로젝트의 ACTIVE 멤버가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 프로젝트",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            )
    })
    ResponseEntity<ApiResponse<List<ActiveProjectMemberResponse>>> getActiveMembers(
            Long projectId,
            Long userId
    );
}
