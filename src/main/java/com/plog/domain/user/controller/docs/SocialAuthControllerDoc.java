package com.plog.domain.user.controller.docs;

import com.plog.domain.user.dto.request.SocialLoginRequest;
import com.plog.domain.user.dto.request.SocialSignupRequest;
import com.plog.domain.user.dto.response.SocialLoginResponse;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Social Auth", description = "카카오 / 구글 소셜 로그인 API")
public interface SocialAuthControllerDoc {

    @Operation(
            summary = "소셜 인증",
            description = """
                    프론트가 provider 콜백에서 받은 인가 코드를 전달하면 서버가 토큰 교환과 이메일 조회를 수행합니다.
                    - 이미 가입된 소셜 계정이면 `status=LOGIN` 과 함께 Access/Refresh 토큰을 반환합니다(AUTH011).
                    - 신규 계정이면 `status=SIGNUP_REQUIRED` 와 가입 티켓을 반환합니다(AUTH012).
                      티켓은 30분간 유효하며 `POST /api/auth/oauth/signup` 에 그대로 전달합니다.
                    - CSRF 방지용 `state` 생성·대조는 프론트엔드 책임입니다(서버는 관여하지 않습니다).
                    - 인가 요청에 사용한 `redirect_uri` 와 서버 설정값이 다르면 provider가 교환을 거부합니다.
                    """
    )
    @SecurityRequirements // 공개 API — 로그인 전 호출
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공(AUTH011) 또는 추가 정보 입력 필요(AUTH012)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "지원하지 않는 provider(AUTH019) 또는 이메일 미제공(AUTH021)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH019",
                                      "message": "지원하지 않는 소셜 로그인입니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인가 코드 교환 실패 (AUTH020)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH020",
                                      "message": "소셜 인증에 실패했습니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "탈퇴 처리 중인 계정 (AUTH016)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이메일 가입자(AUTH001) / 다른 소셜 가입자(AUTH002) / 탈퇴 처리 중인 이메일(AUTH017)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH001",
                                      "message": "이미 사용 중인 이메일입니다."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<SocialLoginResponse>> login(
            @Parameter(description = "kakao | google", example = "kakao") String provider,
            SocialLoginRequest request);

    @Operation(
            summary = "소셜 가입 완료",
            description = """
                    가입 티켓과 약관 동의·프로필 정보를 받아 계정을 만들고 토큰을 발급합니다.
                    - 이메일과 provider 정보는 티켓에서 꺼내므로 요청에 담지 않습니다.
                    - 필수 약관 3종(SERVICE_TERMS, PRIVACY, EXTERNAL_DATA)에 모두 동의해야 합니다.
                    - 실명은 가입 후 1회만 변경할 수 있습니다.
                    - 티켓은 1회용입니다. 이미 사용했거나 30분이 지나면 소셜 인증부터 다시 해야 합니다.
                    """
    )
    @SecurityRequirements // 공개 API — 티켓이 인증 역할
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "가입 완료 (AUTH013)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "필수 약관 미동의(AUTH004) / 티켓 무효(AUTH022) / 티켓 만료(AUTH023)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH023",
                                      "message": "가입 시간이 초과되었습니다. 처음부터 다시 시도해 주세요."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "닉네임 중복(AUTH003) / 이미 가입된 소셜 계정(AUTH025)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH003",
                                      "message": "이미 사용 중인 닉네임입니다."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<TokenResponse>> signup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SocialSignupRequest.class),
                            examples = @ExampleObject(name = "소셜 가입 예시", value = """
                                    {
                                      "ticket": "3xL1n0...",
                                      "name": "홍길동",
                                      "nickname": "조이",
                                      "profilePreset": "OTTER",
                                      "agreements": [
                                        { "agreementType": "SERVICE_TERMS", "agreed": true },
                                        { "agreementType": "PRIVACY", "agreed": true },
                                        { "agreementType": "EXTERNAL_DATA", "agreed": true },
                                        { "agreementType": "MARKETING", "agreed": false }
                                      ]
                                    }
                                    """)
                    )
            )
            SocialSignupRequest request);
}
