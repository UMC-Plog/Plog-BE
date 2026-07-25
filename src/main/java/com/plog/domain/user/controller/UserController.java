package com.plog.domain.user.controller;

import com.plog.domain.user.controller.docs.UserControllerDoc;
import com.plog.domain.user.dto.request.WithdrawalRequest;
import com.plog.domain.user.service.UserWithdrawalService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.UserSuccessCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController implements UserControllerDoc {

    private final UserWithdrawalService userWithdrawalService;

    public UserController(UserWithdrawalService userWithdrawalService) {
        this.userWithdrawalService = userWithdrawalService;
    }

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WithdrawalRequest request
    ) {
        userWithdrawalService.withdraw(userId, Boolean.TRUE.equals(request.agreed()));
        return ResponseEntity.status(UserSuccessCode.WITHDRAWAL_COMPLETED.getHttpStatus())
                .body(ApiResponse.success(UserSuccessCode.WITHDRAWAL_COMPLETED, null));
    }
}
