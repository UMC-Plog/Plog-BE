package com.plog.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatReadUpdateRequest(
        @NotNull(message = "lastReadMessageId는 필수입니다.")
        Long lastReadMessageId
) {
}