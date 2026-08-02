package com.plog.domain.integration.service;

record NotionWebhookTarget(
        String entityId,
        String entityType,
        String parentId,
        String parentType
) {
}
