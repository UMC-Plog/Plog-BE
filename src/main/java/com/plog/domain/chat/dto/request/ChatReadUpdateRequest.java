package com.plog.domain.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatReadUpdateRequest(
        @NotNull(message = "lastReadMessageId는 필수입니다.")
        @Positive(message = "lastReadMessageId는 1 이상이어야 합니다.")
        Long lastReadMessageId
) {
}