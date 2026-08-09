package com.plog.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.report.entity.CompetencyCategory;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ExternalActivityCompetencyMapper {

    private static final Set<CompetencyCategory> OUTPUT = Set.of(CompetencyCategory.OUTPUT);
    private static final Set<CompetencyCategory> OUTPUT_AND_LEADERSHIP = Set.of(
            CompetencyCategory.OUTPUT,
            CompetencyCategory.LEADERSHIP);
    private static final Set<CompetencyCategory> COLLABORATION = Set.of(CompetencyCategory.COLLABORATION);
    private static final Set<CompetencyCategory> COLLABORATION_AND_COMMUNICATION = Set.of(
            CompetencyCategory.COLLABORATION,
            CompetencyCategory.COMMUNICATION);
    private static final Set<CompetencyCategory> LEADERSHIP_AND_COMMUNICATION = Set.of(
            CompetencyCategory.LEADERSHIP,
            CompetencyCategory.COMMUNICATION);
    private static final Set<String> OUTPUT_GOOGLE_DRIVE_ACTIONS = Set.of("create", "edit");

    private final ObjectMapper objectMapper;

    public ExternalActivityCompetencyMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Set<CompetencyCategory> map(IntegrationActivityType activityType, String providerPayload) {
        if (activityType == null) {
            return Set.of();
        }

        return switch (activityType) {
            case GITHUB_COMMIT, FIGMA_FILE_VERSION, GOOGLE_DRIVE_REVISION -> OUTPUT;
            case GITHUB_PULL_REQUEST -> OUTPUT_AND_LEADERSHIP;
            case GITHUB_PULL_REQUEST_REVIEW, GITHUB_ISSUE_COMMENT, FIGMA_COMMENT, GOOGLE_DRIVE_COMMENT,
                    NOTION_COMMENT -> COLLABORATION_AND_COMMUNICATION;
            case GITHUB_ISSUE -> LEADERSHIP_AND_COMMUNICATION;
            case FIGMA_COMMENT_REACTION -> COLLABORATION;
            case GOOGLE_DRIVE_ACTIVITY -> googleDriveActivityCategories(providerPayload);
            case GITHUB_ISSUE_EVENT, NOTION_DATA_SOURCE_SNAPSHOT, NOTION_PAGE_SNAPSHOT, NOTION_BLOCK_SNAPSHOT,
                    NOTION_WEBHOOK_EVENT, GOOGLE_DRIVE_FILE_SNAPSHOT, GOOGLE_DOCUMENT_SUGGESTION,
                    GOOGLE_PRESENTATION_SNAPSHOT, FIGMA_FILE_METADATA -> Set.of();
        };
    }

    private Set<CompetencyCategory> googleDriveActivityCategories(String providerPayload) {
        String action = googleDriveAction(providerPayload);
        return OUTPUT_GOOGLE_DRIVE_ACTIONS.contains(action) ? OUTPUT : Set.of();
    }

    private String googleDriveAction(String providerPayload) {
        if (providerPayload == null || providerPayload.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(providerPayload);
            if (!root.isObject()) {
                return "";
            }
            JsonNode primaryActionDetail = root.path("primaryActionDetail");
            if (primaryActionDetail.isObject()) {
                Iterator<String> fieldNames = primaryActionDetail.fieldNames();
                while (fieldNames.hasNext()) {
                    String action = fieldNames.next().trim().toLowerCase(Locale.ROOT);
                    if (OUTPUT_GOOGLE_DRIVE_ACTIONS.contains(action)) {
                        return action;
                    }
                }
            }
            return root.path("action").asText("").trim().toLowerCase(Locale.ROOT);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
