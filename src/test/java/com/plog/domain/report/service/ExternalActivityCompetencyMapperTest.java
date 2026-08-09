package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.RawActivityType;
import org.junit.jupiter.api.Test;

class ExternalActivityCompetencyMapperTest {

    private final ExternalActivityCompetencyMapper mapper = new ExternalActivityCompetencyMapper(new ObjectMapper());

    @Test
    void mapsExternalRawActivityTypesToCompetencies() {
        assertThat(mapper.map(RawActivityType.GITHUB_COMMIT, "{}"))
                .containsExactly(CompetencyCategory.OUTPUT);
        assertThat(mapper.map(RawActivityType.GITHUB_PULL_REQUEST, "{}"))
                .containsExactlyInAnyOrder(CompetencyCategory.OUTPUT, CompetencyCategory.LEADERSHIP);
        assertThat(mapper.map(RawActivityType.GITHUB_ISSUE, "{}"))
                .containsExactlyInAnyOrder(CompetencyCategory.LEADERSHIP, CompetencyCategory.COMMUNICATION);
        assertThat(mapper.map(RawActivityType.FIGMA_COMMENT_REACTION, "{}"))
                .containsExactly(CompetencyCategory.COLLABORATION);
        assertThat(mapper.map(RawActivityType.NOTION_COMMENT, "{}"))
                .containsExactlyInAnyOrder(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION);
    }

    @Test
    void googleDriveActivityCountsOnlyCreateAndEditAsOutput() {
        assertThat(mapper.map(RawActivityType.GOOGLE_DRIVE_ACTIVITY, "{\"action\":\"create\"}"))
                .containsExactly(CompetencyCategory.OUTPUT);
        assertThat(mapper.map(RawActivityType.GOOGLE_DRIVE_ACTIVITY,
                "{\"primaryActionDetail\":{\"edit\":{}}}"))
                .containsExactly(CompetencyCategory.OUTPUT);
        assertThat(mapper.map(RawActivityType.GOOGLE_DRIVE_ACTIVITY, "{\"action\":\"move\"}"))
                .isEmpty();
        assertThat(mapper.map(RawActivityType.GOOGLE_DRIVE_ACTIVITY, "not-json"))
                .isEmpty();
    }

    @Test
    void deletedGoogleDriveCommentDoesNotMapToCompetency() {
        assertThat(mapper.map(RawActivityType.GOOGLE_DRIVE_COMMENT, "{\"deleted\":true}"))
                .isEmpty();
        assertThat(mapper.map(RawActivityType.GOOGLE_DRIVE_COMMENT, "{\"deleted\":false}"))
                .containsExactlyInAnyOrder(CompetencyCategory.COLLABORATION, CompetencyCategory.COMMUNICATION);
    }

    @Test
    void unsupportedExternalSnapshotsDoNotMapToCompetency() {
        assertThat(mapper.map(RawActivityType.FIGMA_FILE_METADATA, "{}")).isEmpty();
        assertThat(mapper.map(RawActivityType.GOOGLE_PRESENTATION_SNAPSHOT, "{}")).isEmpty();
        assertThat(mapper.map(RawActivityType.NOTION_DATA_SOURCE_SNAPSHOT, "{}")).isEmpty();
        assertThat(mapper.map(RawActivityType.NOTION_PAGE_SNAPSHOT, "{}")).isEmpty();
        assertThat(mapper.map(RawActivityType.NOTION_BLOCK_SNAPSHOT, "{}")).isEmpty();
        assertThat(mapper.map(null, "{}")).isEmpty();
    }
}
