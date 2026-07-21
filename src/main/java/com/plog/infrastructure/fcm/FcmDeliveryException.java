package com.plog.infrastructure.fcm;

public class FcmDeliveryException extends RuntimeException {
    private final boolean invalidToken;

    public FcmDeliveryException(boolean invalidToken, Throwable cause) {
        super(cause);
        this.invalidToken = invalidToken;
    }

    public boolean isInvalidToken() {
        return invalidToken;
    }
}
