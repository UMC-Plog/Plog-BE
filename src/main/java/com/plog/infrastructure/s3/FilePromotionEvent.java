package com.plog.infrastructure.s3;

import java.util.List;

public record FilePromotionEvent(List<String> fileKeys) {
    public FilePromotionEvent {
        fileKeys = List.copyOf(fileKeys);
    }
}
