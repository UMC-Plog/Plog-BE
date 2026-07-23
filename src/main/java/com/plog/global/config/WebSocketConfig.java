package com.plog.global.config;

import com.plog.domain.chat.service.ChatSubscriptionInterceptor;
import com.plog.infrastructure.websocket.BrokerDestinations;
import com.plog.infrastructure.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final ChatSubscriptionInterceptor chatSubscriptionInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(corsProperties.allowedOrigins().toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker(BrokerDestinations.PREFIXES);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 순서 중요: 인증(CONNECT) → 인가(SUBSCRIBE) 순서로 등록
        registration.interceptors(stompAuthChannelInterceptor, chatSubscriptionInterceptor);
    }
}