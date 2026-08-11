package com.plog.domain.user.controller.docs;

import com.plog.domain.user.dto.request.PasswordResetRequest;
import com.plog.domain.user.dto.request.PasswordResetSendRequest;
import com.plog.domain.user.dto.request.PasswordResetVerifyRequest;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "회원가입, 로그인, 소셜 인증, 이메일 인증 및 비밀번호 재설정 API")
public interface PasswordResetControllerDoc {

    @Operation(
            summary = "비밀번호 재설정 인증 코드 발송",
            description = """
                    가입한 이메일로 재설정 인증 코드를 발송합니다.
                    - 가입된 **일반(이메일) 계정**만 대상입니다. 회원가입용 발송과 검사 방향이 반대입니다.
                    - 코드는 6자리이며 5분간 유효합니다. 재전송은 60초 쿨다운이 있습니다.
                    """
    )
    @SecurityRequirements // 공개 API
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "발송 성공 (AUTH008)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "소셜 계정 (AUTH015)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH015",
                                      "message": "소셜 계정은 비밀번호를 재설정할 수 없습니다. 소셜 로그인을 이용해 주세요."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "탈퇴 처리 중인 계정 (AUTH016)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH016",
                                      "message": "탈퇴 처리 중인 계정입니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "가입되지 않은 이메일 (AUTH014)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH014",
                                      "message": "가입되지 않은 이메일입니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", description = "재전송 쿨다운 (AUTH008 error)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH008",
                                      "message": "잠시 후 다시 시도해 주세요."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<Void>> send(PasswordResetSendRequest request);

    @Operation(
            summary = "비밀번호 재설정 인증 코드 검증",
            description = """
                    발송된 코드를 검증합니다. 성공하면 해당 이메일이 재설정 가능 상태가 됩니다.
                    - 불일치 AUTH005, 만료 AUTH006, 5회 초과 AUTH007.
                    - 회원가입용 인증 코드로는 검증되지 않습니다.
                    """
    )
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "인증 성공 (AUTH009)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "코드 불일치(AUTH005) 또는 만료(AUTH006)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "코드 불일치", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH005",
                                              "message": "인증 코드가 일치하지 않습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "코드 만료", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH006",
                                              "message": "인증 코드가 만료되었습니다."
                                            }
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", description = "시도 횟수 초과 (AUTH007)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH007",
                                      "message": "인증 시도 횟수를 초과했습니다. 코드를 다시 발급받아 주세요."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<Void>> verify(PasswordResetVerifyRequest request);

    @Operation(
            summary = "새 비밀번호 설정",
            description = """
                    인증을 완료한 이메일의 비밀번호를 교체합니다.
                    - 인증 코드 검증을 먼저 통과해야 합니다(미인증 AUTH009 error).
                    - 성공 시 해당 계정의 모든 리프레시 토큰이 폐기되어 기존 세션이 모두 로그아웃됩니다.
                    - 비밀번호는 8자 이상, 영문과 숫자를 포함해야 합니다.
                    - newPassword 와 newPasswordConfirm 이 다르면 AUTH018 입니다. 이 경우 아무것도 변경되지 않고
                      인증 상태도 유지되므로, 사용자가 확인 칸만 다시 입력해 재요청할 수 있습니다.
                    """
    )
    @SecurityRequirements
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "재설정 성공 (AUTH010)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "비밀번호 확인 불일치(AUTH018) 또는 이메일 미인증(AUTH009 error) 또는 소셜 계정(AUTH015) 또는 비밀번호 형식 오류(COMMON400)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "비밀번호 확인 불일치", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH018",
                                              "message": "비밀번호와 비밀번호 확인이 일치하지 않습니다."
                                            }
                                            """),
                                    @ExampleObject(name = "이메일 미인증", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH009",
                                              "message": "이메일 인증이 필요합니다."
                                            }
                                            """),
                                    @ExampleObject(name = "소셜 계정", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH015",
                                              "message": "소셜 계정은 비밀번호를 재설정할 수 없습니다. 소셜 로그인을 이용해 주세요."
                                            }
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "탈퇴 처리 중인 계정 (AUTH016)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH016",
                                      "message": "탈퇴 처리 중인 계정입니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "가입되지 않은 이메일 (AUTH014)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH014",
                                      "message": "가입되지 않은 이메일입니다."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<Void>> reset(PasswordResetRequest request);
}
