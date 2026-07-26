package com.plog.domain.user.controller;

import com.plog.domain.user.controller.docs.PasswordResetControllerDoc;
import com.plog.domain.user.dto.request.PasswordResetRequest;
import com.plog.domain.user.dto.request.PasswordResetSendRequest;
import com.plog.domain.user.dto.request.PasswordResetVerifyRequest;
import com.plog.domain.user.service.PasswordResetService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.AuthSuccessCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordResetController implements PasswordResetControllerDoc {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Override
    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> send(@Valid @RequestBody PasswordResetSendRequest request) {
        passwordResetService.sendCode(request.email());
        return success(AuthSuccessCode.PASSWORD_RESET_CODE_SENT);
    }

    @Override
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody PasswordResetVerifyRequest request) {
        passwordResetService.verify(request.email(), request.code());
        return success(AuthSuccessCode.PASSWORD_RESET_EMAIL_VERIFIED);
    }

    @Override
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> reset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.reset(request.email(), request.newPassword(), request.newPasswordConfirm());
        return success(AuthSuccessCode.PASSWORD_RESET_COMPLETED);
    }

    private ResponseEntity<ApiResponse<Void>> success(AuthSuccessCode code) {
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.success(code, null));
    }
}
