package com.plog.e2e.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.e2e.support.E2eTestBase;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("ProjectSettingsController E2E")
class ProjectSettingsControllerE2eTest extends E2eTestBase {

    @Nested
    @DisplayName("GET /projects/{projectId}/settings")
    class GetProjectSettings {

        @Test
        @DisplayName("ACTIVE 멤버에게 프로젝트·초대·본인 외부 연결 정보를 반환한다")
        void success() {
            Long userId = saveUser("settings-reader");
            Long projectId = saveProject("settings-read");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "리더");
            jdbc.update("""
                    insert into external_connection (
                        project_member_id, link_type, external_account_id, created_at, updated_at
                    ) values (?, 'GITHUB', 'github-account', now(), now())
                    """, memberId);
            jdbc.update("""
                    insert into external_connection (
                        project_member_id, link_type, external_account_id, created_at, updated_at
                    ) values (?, 'FIGMA', null, now(), now())
                    """, memberId);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.GET, settings(projectId), userId, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(code(response)).isEqualTo("COMMON200");
            assertThat(result(response).path("projectId").asLong()).isEqualTo(projectId);
            assertThat(result(response).path("invite").path("inviteUrl").asText())
                    .isEqualTo("https://plog.test/invite/invite-settings-read");
            assertThat(result(response).path("invite").path("qrUrl").asText())
                    .endsWith("/invite-settings-read/qr");
            assertThat(result(response).path("externalConnections")).hasSize(2);
            assertThat(result(response).path("externalConnections").toString())
                    .contains("GITHUB", "FIGMA", "true", "false");
        }

        @Test
        @DisplayName("인증·ACTIVE 멤버·프로젝트 존재 여부를 검증한다")
        void policies() {
            Long activeUserId = saveUser("settings-active");
            Long exitedUserId = saveUser("settings-exit");
            Long projectId = saveProject("settings-policy");
            saveMember(activeUserId, projectId, "MEMBER", "ACTIVE", "활성");
            saveMember(exitedUserId, projectId, "MEMBER", "EXIT", "탈퇴");

            ResponseEntity<JsonNode> unauthorized = request(
                    HttpMethod.GET, settings(projectId), null, null);
            assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(code(unauthorized)).isEqualTo("UNAUTHORIZED");

            ResponseEntity<JsonNode> exited = request(
                    HttpMethod.GET, settings(projectId), exitedUserId, null);
            assertThat(exited.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(code(exited)).isEqualTo("PROJECT_MEMBER_REQUIRED");

            ResponseEntity<JsonNode> missing = request(
                    HttpMethod.GET, settings(999_999L), activeUserId, null);
            assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(code(missing)).isEqualTo("PROJECT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("PATCH /projects/{projectId}/settings")
    class PatchProjectSettings {

        @Test
        @DisplayName("OWNER가 전달한 필드만 수정하고 프로젝트명을 trim한다")
        void partialUpdate() {
            Long ownerId = saveUser("settings-owner");
            Long projectId = saveProject("settings-update");
            saveMember(ownerId, projectId, "OWNER", "ACTIVE", "오너");
            LocalDate newEndDay = LocalDate.now(ZoneOffset.UTC).plusDays(45);

            ResponseEntity<JsonNode> nameOnly = request(
                    HttpMethod.PATCH,
                    settings(projectId),
                    ownerId,
                    Map.of("projectName", "  Plog 리뉴얼  ")
            );
            assertThat(result(nameOnly).path("projectName").asText()).isEqualTo("Plog 리뉴얼");
            assertThat(result(nameOnly).path("projectType").asText()).isEqualTo("DEVELOP");

            ResponseEntity<JsonNode> dateAndType = request(
                    HttpMethod.PATCH,
                    settings(projectId),
                    ownerId,
                    Map.of("endDay", newEndDay.toString(), "projectType", "GENERAL")
            );
            assertThat(result(dateAndType).path("projectName").asText()).isEqualTo("Plog 리뉴얼");
            assertThat(result(dateAndType).path("projectType").asText()).isEqualTo("GENERAL");
            assertThat(result(dateAndType).path("endDay").asText()).isEqualTo(newEndDay.toString());
        }

        @Test
        @DisplayName("일반 멤버의 변경을 거부하고 이름·종료일 정책을 검증한다")
        void policies() {
            Long memberUserId = saveUser("settings-member");
            Long ownerId = saveUser("settings-validator");
            Long projectId = saveProject("settings-validation");
            saveMember(memberUserId, projectId, "MEMBER", "ACTIVE", "멤버");
            saveMember(ownerId, projectId, "OWNER", "ACTIVE", "오너");

            ResponseEntity<JsonNode> denied = request(
                    HttpMethod.PATCH, settings(projectId), memberUserId, Map.of("projectName", "변경 시도"));
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(code(denied)).isEqualTo("PROJECT_SETTING_PERMISSION_DENIED");

            ResponseEntity<JsonNode> invalidName = request(
                    HttpMethod.PATCH, settings(projectId), ownerId, Map.of("projectName", " 한 "));
            assertThat(code(invalidName)).isEqualTo("VALIDATION_ERROR");

            ResponseEntity<JsonNode> invalidEndDay = request(
                    HttpMethod.PATCH,
                    settings(projectId),
                    ownerId,
                    Map.of("endDay", LocalDate.now(ZoneOffset.UTC).toString())
            );
            assertThat(code(invalidEndDay)).isEqualTo("VALIDATION_ERROR");
        }
    }

    private String settings(Long projectId) {
        return "/projects/" + projectId + "/settings";
    }
}
