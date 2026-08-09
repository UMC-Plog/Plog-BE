package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.IntegrationActivityType;
import com.plog.domain.report.entity.CompetencyCategory;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalActivityCompetencyMapperTest {

    private final ExternalActivityCompetencyMapper mapper = new ExternalActivityCompetencyMapper(new ObjectMapper());

    @Test
    void 모든_연동_활동_타입을_정확한_역량으로_매핑한다() {
        Map<IntegrationActivityType, Set<CompetencyCategory>> expected =
                new EnumMap<>(IntegrationActivityType.class);
        expected.put(IntegrationActivityType.GITHUB_COMMIT, Set.of(CompetencyCategory.OUTPUT));
        expected.put(IntegrationActivityType.GITHUB_PULL_REQUEST,
                Set.of(CompetencyCategory.OUTPUT, CompetencyCategory.LEADERSHIP));
        expected.put(IntegrationActivityType.GITHUB_PULL_REQUEST_REVIEW,
                Set.of(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION));
        expected.put(IntegrationActivityType.GITHUB_ISSUE,
                Set.of(CompetencyCategory.LEADERSHIP, CompetencyCategory.COMMUNICATION));
        expected.put(IntegrationActivityType.GITHUB_ISSUE_COMMENT,
                Set.of(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION));
        expected.put(IntegrationActivityType.GITHUB_ISSUE_EVENT, Set.of());
        expected.put(IntegrationActivityType.NOTION_DATA_SOURCE_SNAPSHOT, Set.of());
        expected.put(IntegrationActivityType.NOTION_PAGE_SNAPSHOT, Set.of());
        expected.put(IntegrationActivityType.NOTION_BLOCK_SNAPSHOT, Set.of());
        expected.put(IntegrationActivityType.NOTION_COMMENT,
                Set.of(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION));
        expected.put(IntegrationActivityType.NOTION_WEBHOOK_EVENT, Set.of());
        expected.put(IntegrationActivityType.GOOGLE_DRIVE_FILE_SNAPSHOT, Set.of());
        expected.put(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, Set.of(CompetencyCategory.OUTPUT));
        expected.put(IntegrationActivityType.GOOGLE_DRIVE_COMMENT,
                Set.of(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION));
        expected.put(IntegrationActivityType.GOOGLE_DRIVE_REVISION, Set.of(CompetencyCategory.OUTPUT));
        expected.put(IntegrationActivityType.GOOGLE_DOCUMENT_SUGGESTION, Set.of());
        expected.put(IntegrationActivityType.GOOGLE_PRESENTATION_SNAPSHOT, Set.of());
        expected.put(IntegrationActivityType.FIGMA_FILE_VERSION, Set.of(CompetencyCategory.OUTPUT));
        expected.put(IntegrationActivityType.FIGMA_FILE_METADATA, Set.of());
        expected.put(IntegrationActivityType.FIGMA_COMMENT,
                Set.of(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION));
        expected.put(IntegrationActivityType.FIGMA_COMMENT_REACTION, Set.of(CompetencyCategory.COLLABORATION));

        assertThat(expected.keySet()).containsExactlyInAnyOrder(IntegrationActivityType.values());
        for (IntegrationActivityType activityType : IntegrationActivityType.values()) {
            String payload = activityType == IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY
                    ? "{\"action\":\"create\"}"
                    : "{}";
            assertThat(mapper.map(activityType, payload))
                    .as(activityType.name())
                    .containsExactlyInAnyOrderElementsOf(expected.get(activityType));
        }
    }

    @Test
    void 구글_드라이브_활동은_create_edit_action만_산출물로_매핑한다() {
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, "{\"action\":\"create\"}"))
                .containsExactly(CompetencyCategory.OUTPUT);
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, "{\"action\":\"edit\"}"))
                .containsExactly(CompetencyCategory.OUTPUT);
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                "{\"primaryActionDetail\":{\"create\":{}}}"))
                .containsExactly(CompetencyCategory.OUTPUT);
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                "{\"primaryActionDetail\":{\"edit\":{}}}"))
                .containsExactly(CompetencyCategory.OUTPUT);

        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, "{\"action\":\"move\"}")).isEmpty();
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY,
                "{\"primaryActionDetail\":{\"move\":{}}}")).isEmpty();
    }

    @Test
    void 구글_드라이브_활동_payload가_잘못되면_빈_매핑을_반환한다() {
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, null)).isEmpty();
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, "")).isEmpty();
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, "not-json")).isEmpty();
        assertThat(mapper.map(IntegrationActivityType.GOOGLE_DRIVE_ACTIVITY, "[]")).isEmpty();
        assertThat(mapper.map(null, "{\"action\":\"create\"}")).isEmpty();
    }
}
