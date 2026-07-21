package com.plog.domain.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProjectJoinDtoTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void deserializesTheProjectJoinRequestContract() throws Exception {
        ProjectJoinRequest request = objectMapper.readValue(
                "{\"inviteCode\":\"PLOG-ABCD12\"}",
                ProjectJoinRequest.class
        );

        assertThat(request.inviteCode()).isEqualTo("PLOG-ABCD12");
    }

    @Test
    void masksTheInviteCodeInDiagnosticText() {
        ProjectJoinRequest request = new ProjectJoinRequest("PLOG-SECRET-CODE");

        assertThat(request.toString())
                .contains("inviteCode=***")
                .doesNotContain("PLOG-SECRET-CODE");
    }

    @Test
    void serializesProjectAndMemberStatusesWithoutAmbiguity() {
        ProjectJoinResponse response = new ProjectJoinResponse(
                10L,
                "Plog API",
                25L,
                ProjectRole.MEMBER,
                ProjectStatus.IN_PROGRESS,
                MemberStatus.ACTIVE,
                Instant.parse("2026-07-20T21:00:00Z")
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("projectId").longValue()).isEqualTo(10L);
        assertThat(json.path("projectName").textValue()).isEqualTo("Plog API");
        assertThat(json.path("projectMemberId").longValue()).isEqualTo(25L);
        assertThat(json.path("role").textValue()).isEqualTo("MEMBER");
        assertThat(json.path("projectStatus").textValue()).isEqualTo("IN_PROGRESS");
        assertThat(json.path("memberStatus").textValue()).isEqualTo("ACTIVE");
        // 오프셋을 실어 보낸다 — 클라이언트가 서버 타임존을 추측하지 않도록.
        assertThat(json.path("joinedAt").textValue()).isEqualTo("2026-07-20T21:00:00Z");
        assertThat(json.has("status")).isFalse();
    }
}
