package com.plog.infrastructure.fcm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "plog.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class DisabledFcmGateway implements FcmGateway {
    @Override
    public void send(FcmMessage message) {
        log.debug("fcm_delivery_skipped reason=disabled tokenLength={}", message.token().length());
    }
}
