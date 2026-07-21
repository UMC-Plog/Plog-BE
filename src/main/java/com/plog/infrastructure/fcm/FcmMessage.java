package com.plog.infrastructure.fcm;

import java.util.Map;

public record FcmMessage(
        String token,
        String title,
        String body,
        Map<String, String> data
) {
    public FcmMessage {
        data = Map.copyOf(data);
    }
}
