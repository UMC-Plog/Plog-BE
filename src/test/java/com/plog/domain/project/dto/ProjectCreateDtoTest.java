package com.plog.domain.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProjectCreateDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void deserializesTheProjectCreationRequestContract() throws Exception {
        String json = """
                {
                  "projectName": " Plog API ",
                  "projectType": "DEVELOP",
                  "endDay": "2026-08-31"
                }
                """;

        ProjectCreateRequest request = objectMapper.readValue(json, ProjectCreateRequest.class);

        assertThat(request.projectName()).isEqualTo(" Plog API ");
        assertThat(request.projectType()).isEqualTo(ProjectType.DEVELOP);
        assertThat(request.endDay()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void serializesTheProjectCreationResponseContract() {
        ProjectCreateResponse response = new ProjectCreateResponse(
                10L,
                "Plog API",
                ProjectType.DEVELOP,
                ProjectStatus.IN_PROGRESS,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 8, 31),
                20L,
                ProjectRole.OWNER,
                new ProjectCreateResponse.Invite("invite-code", "https://plog.test/invites/invite-code")
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("projectId").longValue()).isEqualTo(10L);
        assertThat(json.path("projectName").textValue()).isEqualTo("Plog API");
        assertThat(json.path("projectType").textValue()).isEqualTo("DEVELOP");
        assertThat(json.path("status").textValue()).isEqualTo("IN_PROGRESS");
        assertThat(json.path("startDay").textValue()).isEqualTo("2026-07-20");
        assertThat(json.path("endDay").textValue()).isEqualTo("2026-08-31");
        assertThat(json.path("myProjectMemberId").longValue()).isEqualTo(20L);
        assertThat(json.path("myRole").textValue()).isEqualTo("OWNER");
        assertThat(json.path("invite").path("inviteCode").textValue()).isEqualTo("invite-code");
        assertThat(json.path("invite").path("inviteUrl").textValue())
                .isEqualTo("https://plog.test/invites/invite-code");
    }
}
