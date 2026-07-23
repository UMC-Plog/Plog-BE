package com.plog.infrastructure.s3;

import java.util.List;

public record PostFileDeletionEvent(List<String> fileKeys) {
    public PostFileDeletionEvent {
        fileKeys = List.copyOf(fileKeys);
    }
}
