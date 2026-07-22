package com.plog.global.api.response;

import java.util.List;
import org.springframework.data.domain.Slice;

public record SliceResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext
) {

    public SliceResponse {
        content = List.copyOf(content);
    }

    public static <T> SliceResponse<T> of(Slice<?> slice, List<T> content) {
        return new SliceResponse<>(content, slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
