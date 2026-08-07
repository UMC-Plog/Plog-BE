package com.plog.infrastructure.fcm.e2e;

import com.plog.infrastructure.fcm.FcmMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "plog.e2e",
        name = "fcm-delivery-probe-enabled",
        havingValue = "true"
)
public class FcmDeliveryProbe {
    private static final long POLL_INTERVAL_MILLIS = 50L;

    private final Map<String, CopyOnWriteArrayList<Delivery>> deliveriesByResourceId =
            new ConcurrentHashMap<>();

    public void record(FcmMessage message, String providerMessageId) {
        String resourceId = message.data().get("resourceId");
        if (resourceId == null || resourceId.isBlank()) {
            return;
        }
        Delivery delivery = new Delivery(
                providerMessageId,
                tokenSuffix(message.token()),
                message.title(),
                message.body(),
                message.data(),
                Instant.now()
        );
        deliveriesByResourceId
                .computeIfAbsent(resourceId, ignored -> new CopyOnWriteArrayList<>())
                .add(delivery);
    }

    public List<Delivery> await(String resourceId, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(timeoutMillis, 0L);
        do {
            List<Delivery> deliveries = deliveriesByResourceId.get(resourceId);
            if (deliveries != null && !deliveries.isEmpty()) {
                return List.copyOf(deliveries);
            }
            if (!sleep()) {
                break;
            }
        } while (System.currentTimeMillis() < deadline);
        return List.of();
    }

    private boolean sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String tokenSuffix(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        return token.substring(Math.max(0, token.length() - 8));
    }

    public record Delivery(
            String providerMessageId,
            String tokenSuffix,
            String title,
            String body,
            Map<String, String> data,
            Instant acceptedAt
    ) {
        public Delivery {
            data = Map.copyOf(data);
        }
    }
}
