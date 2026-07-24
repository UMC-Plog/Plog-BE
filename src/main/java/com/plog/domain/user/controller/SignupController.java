package com.plog.domain.user.controller;

import com.plog.domain.user.dto.request.SignupRequest;
import com.plog.domain.user.service.UserService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.AuthSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Signup", description = "회원가입 / 닉네임 중복확인 API")
@Validated
@RestController
@RequestMapping("/api/auth")
public class SignupController {

    private final UserService userService;

    public SignupController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "닉네임 중복확인",
            description = """
                    닉네임 사용 가능 여부를 확인합니다.
                    - 사용 가능하면 AUTH003(NICKNAME_AVAILABLE) 성공, 이미 사용 중이면 AUTH003(NICKNAME_DUPLICATED) 409.
                    - 이 확인만 믿지 마세요. 확인~가입 사이 선점이 가능하며 최종 방어선은 가입 시 유니크 제약입니다.
                    """
    )
    @SecurityRequirements // 공개 API
    @GetMapping("/nickname/check")
    public ResponseEntity<ApiResponse<Void>> checkNickname(@RequestParam @NotBlank String nickname) {
        userService.checkNicknameAvailable(nickname);
        return success(AuthSuccessCode.NICKNAME_AVAILABLE);
    }

    @Operation(
            summary = "회원가입",
            description = """
                    실명/이메일/비밀번호/닉네임/약관동의를 한 번에 받아 가입합니다(2단계 폼, 1 API).
                    - 사전 조건: 해당 이메일이 인증 완료 상태여야 합니다(다른 이메일로 가입 시 AUTH009).
                    - 필수 약관(서비스 이용약관/개인정보/외부데이터) 미동의 시 AUTH004. 마케팅은 선택.
                    - 비밀번호는 8자 이상 + 영문/숫자 포함(위반 시 COMMON400).
                    - 프로필 프리셋(profilePreset)은 선택 — 미선택(null) 시 기본 아바타. 커스텀 업로드는 없습니다.
                    - 이메일 중복 AUTH001(일반)/AUTH002(소셜), 닉네임 중복 AUTH003.
                    """
    )
    @SecurityRequirements // 공개 API
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "가입 예시", value = """
                                    {
                                      "name": "홍길동",
                                      "email": "user@example.com",
                                      "password": "plog1234",
                                      "nickname": "gildong",
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
            SignupRequest request) {
        userService.signup(request);
        return success(AuthSuccessCode.SIGNUP_COMPLETED);
    }

    private ResponseEntity<ApiResponse<Void>> success(AuthSuccessCode code) {
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.success(code, null));
    }
}
