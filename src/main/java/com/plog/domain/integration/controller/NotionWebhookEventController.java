package com.plog.domain.integration.controller;

import com.plog.domain.integration.service.NotionWebhookEventIngestionService;
import com.plog.domain.integration.service.NotionWebhookEventIngestionService.IngestionResult;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 API가 아니라 Notion이 호출하는 공개 이벤트 수신 경계다. */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/integrations/notion/events")
public class NotionWebhookEventController {

    private final NotionWebhookEventIngestionService ingestionService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Notion-Signature", required = false) String signature
    ) {
        IngestionResult result;
        try {
            result = ingestionService.ingest(rawBody, signature);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (result == IngestionResult.INVALID_SIGNATURE) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (result == IngestionResult.EVENT_ACCEPTED) {
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.ok().build();
    }
}
