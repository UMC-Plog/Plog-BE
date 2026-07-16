package com.plog.infrastructure.s3;

import java.util.List;

public record FileDeletionEvent(List<String> fileKeys) {
    public FileDeletionEvent {
        fileKeys = List.copyOf(fileKeys);
    }
}
