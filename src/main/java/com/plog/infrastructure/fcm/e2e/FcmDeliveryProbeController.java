package com.plog.infrastructure.fcm.e2e;

import com.plog.infrastructure.fcm.e2e.FcmDeliveryProbe.Delivery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/e2e/fcm-deliveries")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "plog.e2e",
        name = "fcm-delivery-probe-enabled",
        havingValue = "true"
)
public class FcmDeliveryProbeController {
    private static final long MAX_TIMEOUT_MILLIS = 30_000L;

    private final FcmDeliveryProbe probe;

    @GetMapping("/{resourceId}")
    public ResponseEntity<List<Delivery>> get(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "10000") long timeoutMs
    ) {
        long boundedTimeout = Math.max(0L, Math.min(timeoutMs, MAX_TIMEOUT_MILLIS));
        List<Delivery> deliveries = probe.await(resourceId, boundedTimeout);
        return deliveries.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(deliveries);
    }
}
