package com.plog.domain.user.controller.docs;

import com.plog.domain.user.dto.request.WithdrawalRequest;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "User", description = "계정 자체를 다루는 API (인증 필요). 프로필 수정은 Profile 태그를 참고하세요.")
public interface UserControllerDoc {

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    로그인한 계정을 탈퇴 처리합니다.
                    - agreed가 true여야 합니다(USER002).
                    - 즉시: 로그인 차단(AUTH016), 모든 세션·푸시 토큰 폐기, 참여 중인 모든 프로젝트에서 퇴장.
                      본인이 OWNER인 프로젝트는 남은 활성 멤버 중 최초 합류자에게 소유권이 자동 이전되고,
                      혼자인 프로젝트는 함께 삭제됩니다.
                    - 7일 후: 이메일·실명·닉네임 등 개인정보가 파기됩니다. 그때까지 같은 이메일로 재가입할 수 없습니다(AUTH017).
                    - 작성한 게시글·댓글·채팅은 남습니다(다른 멤버의 화면 보존).
                    - 탈퇴 철회는 제공하지 않습니다.
                    - 이미 발급된 액세스 토큰은 만료(최대 30분)까지 유효합니다. 프론트는 탈퇴 성공 시 토큰을 즉시 폐기하세요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "탈퇴 완료 (USER001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "탈퇴 동의 누락 (USER002)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "USER002",
                                      "message": "탈퇴 동의가 필요합니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 헤더가 없거나(COMMON401) 유효하지 않은 토큰(AUTH011)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "인증 헤더 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401",
                                              "message": "인증이 필요합니다."
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH011",
                                              "message": "유효하지 않은 토큰입니다."
                                            }
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "이미 탈퇴 처리된 계정 (AUTH016)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH016",
                                      "message": "탈퇴 처리 중인 계정입니다."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<Void>> withdraw(Long userId, WithdrawalRequest request);
}
