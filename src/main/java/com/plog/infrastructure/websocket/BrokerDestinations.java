package com.plog.infrastructure.websocket;

public final class BrokerDestinations {

    // enableSimpleBroker와 StompAuthChannelInterceptor 양쪽에서 값이 어긋나면 안 되므로 한 곳에 모음.
    public static final String[] PREFIXES = {"/topic", "/queue"};

    private BrokerDestinations() {
    }
}