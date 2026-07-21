package com.plog.domain.user.controller;

import com.plog.domain.user.dto.request.LoginRequest;
import com.plog.domain.user.dto.request.TokenRefreshRequest;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.service.AuthService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.AuthSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인 / 토큰 재발급 / 로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "로그인",
            description = """
                    이메일/비밀번호로 로그인해 Access Token과 Refresh Token을 발급받습니다.
                    - Access Token은 매 요청 `Authorization: Bearer <token>` 헤더로 전송합니다.
                    - Refresh Token은 클라이언트가 보관하다 재발급/로그아웃 시 전송합니다.
                    - 실패 시 이메일/비밀번호 구분 없이 AUTH010(LOGIN_FAILED)을 반환합니다.
                    """
    )
    @SecurityRequirements // 공개 API — Swagger 자물쇠 제거
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = authService.login(request.email(), request.password());
        return success(AuthSuccessCode.LOGIN_SUCCESS, tokens);
    }

    @Operation(
            summary = "토큰 재발급",
            description = """
                    보관 중인 Refresh Token으로 Access/Refresh Token을 재발급받습니다.
                    - 재발급 시 기존 Refresh Token은 폐기되고 새 토큰으로 교체됩니다(회전).
                    - 토큰이 없거나 만료된 경우 AUTH013(INVALID_REFRESH_TOKEN)을 반환합니다.
                    """
    )
    @SecurityRequirements // 공개 API — 만료된 Access 없이도 호출 가능해야 함
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(@Valid @RequestBody TokenRefreshRequest request) {
        TokenResponse tokens = authService.reissue(request.refreshToken());
        return success(AuthSuccessCode.TOKEN_REISSUED, tokens);
    }

    @Operation(
            summary = "로그아웃",
            description = """
                    전달한 Refresh Token을 폐기합니다(해당 기기 세션 종료).
                    - Access Token 인증이 필요 없습니다. Refresh 원문을 가진 본인만 해당 토큰을 폐기할 수 있어,
                      Access가 만료/탈취된 상황에서도 세션을 확실히 종료할 수 있습니다.
                    - 이미 폐기된 토큰이어도 성공 응답합니다(멱등).
                    """
    )
    @SecurityRequirements // 공개 API — Access 만료 상태에서도 로그아웃 가능해야 함
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logout(request.refreshToken());
        return success(AuthSuccessCode.LOGOUT_SUCCESS, null);
    }

    private <T> ResponseEntity<ApiResponse<T>> success(AuthSuccessCode code, T result) {
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.success(code, result));
    }
}
