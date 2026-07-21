package com.plog.e2e.notification;

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

@DisplayName("FcmTokenController E2E")
class FcmTokenControllerE2eTest extends E2eTestBase {

    @Nested
    @DisplayName("PUT /users/me/fcm-token")
    class PutFcmToken {

        @Test
        @DisplayName("토큰 등록은 멱등적이고 다른 사용자가 등록하면 소유권을 이전한다")
        void idempotentUpsertAndOwnershipTransfer() {
            Long firstUserId = saveUser("fcm-first");
            Long secondUserId = saveUser("fcm-second");

            ResponseEntity<JsonNode> created = request(
                    HttpMethod.PUT, tokenPath(), firstUserId, Map.of("token", " device-token "));
            ResponseEntity<JsonNode> duplicate = request(
                    HttpMethod.PUT, tokenPath(), firstUserId, Map.of("token", "device-token"));

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(code(created)).isEqualTo("COMMON200");
            assertThat(result(created).path("token").asText()).isEqualTo("device-token");
            assertThat(result(duplicate).path("fcmId").asLong())
                    .isEqualTo(result(created).path("fcmId").asLong());
            assertThat(fcmCount("device-token")).isEqualTo(1L);

            ResponseEntity<JsonNode> transferred = request(
                    HttpMethod.PUT, tokenPath(), secondUserId, Map.of("token", "device-token"));
            assertThat(result(transferred).path("userId").asLong()).isEqualTo(secondUserId);
            assertThat(jdbc.queryForObject(
                    "select user_id from fcm where token = ?", Long.class, "device-token"))
                    .isEqualTo(secondUserId);
        }

        @Test
        @DisplayName("인증이 없거나 토큰이 비어 있으면 저장하지 않는다")
        void policies() {
            Long userId = saveUser("fcm-policy");

            ResponseEntity<JsonNode> unauthorized = request(
                    HttpMethod.PUT, tokenPath(), null, Map.of("token", "device-token"));
            assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(code(unauthorized)).isEqualTo("UNAUTHORIZED");

            ResponseEntity<JsonNode> invalid = request(
                    HttpMethod.PUT, tokenPath(), userId, Map.of("token", "   "));
            assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(code(invalid)).isEqualTo("VALIDATION_ERROR");
            assertThat(jdbc.queryForObject("select count(*) from fcm", Long.class)).isZero();
        }
    }

    @Nested
    @DisplayName("DELETE /users/me/fcm-token")
    class DeleteFcmToken {

        @Test
        @DisplayName("토큰을 물리 삭제하고 중복 삭제를 멱등적으로 처리한다")
        void physicalAndIdempotentDelete() {
            Long userId = saveUser("fcm-delete");
            request(HttpMethod.PUT, tokenPath(), userId, Map.of("token", "delete-token"));

            ResponseEntity<JsonNode> deleted = request(
                    HttpMethod.DELETE, tokenPath(), userId, Map.of("token", "delete-token"));
            ResponseEntity<JsonNode> duplicate = request(
                    HttpMethod.DELETE, tokenPath(), userId, Map.of("token", "delete-token"));

            assertThat(result(deleted).path("deleted").asBoolean()).isTrue();
            assertThat(result(duplicate).path("deleted").asBoolean()).isTrue();
            assertThat(fcmCount("delete-token")).isZero();
        }
    }

    private long fcmCount(String token) {
        return jdbc.queryForObject(
                "select count(*) from fcm where token = ?", Long.class, token);
    }

    private String tokenPath() {
        return "/users/me/fcm-token";
    }
}
