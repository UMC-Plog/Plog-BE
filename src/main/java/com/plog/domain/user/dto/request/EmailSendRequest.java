package com.plog.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailSendRequest(
        @NotBlank @Email String email
) {
}
