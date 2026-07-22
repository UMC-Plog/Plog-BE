package com.plog.infrastructure.fcm;

public interface FcmGateway {
    void send(FcmMessage message);
}
