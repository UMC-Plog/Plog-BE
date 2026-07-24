package com.plog.domain.user.controller;

import com.plog.domain.user.dto.request.EmailSendRequest;
import com.plog.domain.user.dto.request.EmailVerifyRequest;
import com.plog.domain.user.service.EmailVerificationService;
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

@Tag(name = "Email Verification", description = "회원가입용 이메일 인증 코드 발송 / 검증 API")
@RestController
@RequestMapping("/api/auth/email")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Operation(
            summary = "이메일 인증 코드 발송",
            description = """
                    입력한 이메일로 인증 코드를 발송합니다.
                    - 이미 가입된 이메일이면 발송하지 않고 AUTH001(일반)/AUTH002(소셜)로 가입 수단을 알려줍니다.
                    - 재전송은 60초 쿨다운이 있습니다(AUTH008).
                    - 코드는 5분간 유효합니다.
                    """
    )
    @SecurityRequirements // 공개 API
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> send(@Valid @RequestBody EmailSendRequest request) {
        emailVerificationService.sendCode(request.email());
        return success(AuthSuccessCode.EMAIL_CODE_SENT);
    }

    @Operation(
            summary = "이메일 인증 코드 검증",
            description = """
                    발송된 인증 코드를 검증합니다. 성공하면 해당 이메일이 인증 상태로 표시되어 회원가입에 사용됩니다.
                    - 코드 불일치: AUTH005, 만료: AUTH006.
                    - 5회 실패 시 코드가 폐기됩니다(AUTH007). 이 경우 코드를 다시 발급받아야 합니다.
                    - 인증한 이메일과 실제 가입 이메일이 다르면 가입 단계에서 거부됩니다.
                    """
    )
    @SecurityRequirements // 공개 API
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody EmailVerifyRequest request) {
        emailVerificationService.verify(request.email(), request.code());
        return success(AuthSuccessCode.EMAIL_VERIFIED);
    }

    private ResponseEntity<ApiResponse<Void>> success(AuthSuccessCode code) {
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.success(code, null));
    }
}
