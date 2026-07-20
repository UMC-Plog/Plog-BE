package com.plog.domain.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.project.dto.response.ProjectInviteReissueResponse;
import org.junit.jupiter.api.Test;

class ProjectInviteReissueResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesTheInviteReissueContract() {
        ProjectInviteReissueResponse response = new ProjectInviteReissueResponse(
                "new-invite-code",
                "https://plog.test/invite/new-invite-code",
                true
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("inviteCode").textValue()).isEqualTo("new-invite-code");
        assertThat(json.path("inviteUrl").textValue())
                .isEqualTo("https://plog.test/invite/new-invite-code");
        assertThat(json.path("previousInviteInvalidated").booleanValue()).isTrue();
    }

    @Test
    void redactsTheRawInviteFromDiagnosticOutput() {
        ProjectInviteReissueResponse response = new ProjectInviteReissueResponse(
                "new-invite-code",
                "https://plog.test/invite/new-invite-code",
                true
        );

        assertThat(response.toString())
                .doesNotContain("new-invite-code")
                .doesNotContain("https://plog.test/invite/new-invite-code")
                .contains("REDACTED")
                .contains("previousInviteInvalidated=true");
    }
}
