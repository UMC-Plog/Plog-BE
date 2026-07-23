package com.plog.infrastructure.websocket;

import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SEND -> rejectBrokerDestination(accessor);
            default -> { /* SUBSCRIBE 인가는 도메인별 인터셉터(ChatSubscriptionInterceptor)가 처리 */ }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        try {
            Long userId = jwtProvider.parseUserId(authHeader.substring(BEARER_PREFIX.length()));
            accessor.setUser(new StompPrincipal(userId));
            accessor.getSessionAttributes().put("userId", userId);
        } catch (JwtException exception) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN, exception);
        }
    }

    // 클라이언트가 브로커 목적지(/topic, /queue)로 직접 SEND하면 방 인가를 우회해 위조 메시지를
    // 구독자에게 그대로 뿌릴 수 있다. 클라이언트 SEND는 /app/** (핸들러 경유)만 허용한다.
    private void rejectBrokerDestination(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        boolean targetsBroker = Arrays.stream(BrokerDestinations.PREFIXES)
                .anyMatch(destination::startsWith);
        if (targetsBroker) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
    }
}