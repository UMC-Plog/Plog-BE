package com.plog.e2e.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.e2e.support.E2eTestBase;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("ProjectRoleController E2E")
class ProjectRoleControllerE2eTest extends E2eTestBase {

    @Nested
    @DisplayName("PATCH /api/v1/projects/{projectId}/members/{targetMemberId}/role")
    class PatchProjectRole {

        @Test
        @DisplayName("OWNER가 다른 ACTIVE 팀원에게 방장 권한을 위임한다")
        void success() {
            Long ownerUserId = saveUser("role-owner");
            Long targetUserId = saveUser("role-target");
            Long projectId = saveProject("role-transfer");
            Long ownerMemberId = saveMember(ownerUserId, projectId, "OWNER", "ACTIVE", "오너");
            Long targetMemberId = saveMember(targetUserId, projectId, "MEMBER", "ACTIVE", "대상");

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.PATCH,
                    rolePath(projectId, targetMemberId),
                    ownerUserId,
                    Map.of("role", "OWNER")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(code(response)).isEqualTo("PROJ200_3");
            assertThat(result(response).path("projectId").asLong()).isEqualTo(projectId);
            assertThat(result(response).path("newOwnerMemberId").asLong()).isEqualTo(targetMemberId);
            assertThat(jdbc.queryForObject(
                    "select role from project_members where project_member_id = ?",
                    String.class,
                    ownerMemberId)).isEqualTo("MEMBER");
            assertThat(jdbc.queryForObject(
                    "select role from project_members where project_member_id = ?",
                    String.class,
                    targetMemberId)).isEqualTo("OWNER");
        }

        @Test
        @DisplayName("대상이 ACTIVE 멤버가 아니면 거부한다")
        void rejectsMissingTarget() {
            Long ownerUserId = saveUser("role-owner-missing-target");
            Long projectId = saveProject("role-missing-target");
            saveMember(ownerUserId, projectId, "OWNER", "ACTIVE", "오너");

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.PATCH,
                    rolePath(projectId, 9_999L),
                    ownerUserId,
                    Map.of("role", "OWNER")
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(code(response)).isEqualTo("MEMBER_NOT_FOUND");
        }
    }

    private String rolePath(Long projectId, Long targetMemberId) {
        return "/api/v1/projects/" + projectId + "/members/" + targetMemberId + "/role";
    }
}
