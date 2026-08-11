package com.plog.apitest.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;

import com.plog.apitest.support.ApiTestConfig;
import com.plog.apitest.support.client.AuthApiClient;
import com.plog.apitest.support.client.NotificationApiClient;
import com.plog.apitest.support.client.PostApiClient;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 공지 작성 알림 API
 *
 * APIs:
 * - POST /api/auth/login
 * - POST /api/projects/{projectId}/posts
 * - GET /api/notifications
 * - DELETE /api/projects/{projectId}/posts/{postId}
 */
@Tag("live")
@Tag("regression")
@Tag("destructive")
class NoticeNotificationApiTest {

    /**
     * 공지 게시글 작성 알림
     *
     * <p>API: {@code POST /api/projects/{projectId}/posts}</p>
     * <p>인증: 이메일과 비밀번호로 로그인해 발급받은 Access Token 필요</p>
     * <p>요청: projectId 경로 변수</p>
     * <p>요청 Body:</p>
     * <pre>{@code
     * {
     *   "title": "공지 제목",
     *   "content": "공지 본문",
     *   "isNotice": true,
     *   "attachments": []
     * }
     * }</pre>
     * <p>성공: HTTP 201, {@code COMMON201}</p>
     *
     * <p>응답 Body:</p>
     * <pre>{@code
     * {
     *   "isSuccess": true,
     *   "code": "COMMON201",
     *   "result": {
     *     "postId": 123,
     *     "projectId": 1,
     *     "isNotice": true
     *   }
     * }
     * }</pre>
     *
     * <p>핵심 계약:</p>
     * <ul>
     *   <li>ACTIVE 프로젝트 멤버가 공지 게시글을 작성하면 공지로 저장한다.</li>
     *   <li>작성된 게시글을 resourceId로 갖는 NOTICE 알림을 로그인 사용자의 알림 목록에서 조회할 수 있다.</li>
     * </ul>
     *
     * <p>상태 변경: 공지 게시글과 프로젝트 멤버 대상 알림을 생성하며, 테스트가 만든 게시글은 종료 시 삭제한다.</p>
     */
    @Nested
    class 공지_게시글_작성_알림 {

        @Test
        void 공지_게시글을_작성하면_NOTICE_알림이_생성된다() {
            // 준비 과정
            ApiTestConfig config = ApiTestConfig.fromEnvironment();
            Response loginResponse = new AuthApiClient(config.baseUrl()).login(Map.of(
                    "email", config.email(),
                    "password", config.password()
            ));
            String accessToken = loginResponse.then()
                    .statusCode(200)
                    .body("code", equalTo("AUTH005"))
                    .body("result.accessToken", not(blankOrNullString()))
                    .extract()
                    .jsonPath()
                    .getString("result.accessToken");

            PostApiClient postApiClient = new PostApiClient(config.baseUrl(), accessToken);
            NotificationApiClient notificationApiClient =
                    new NotificationApiClient(config.baseUrl(), accessToken);
            String uniqueSuffix = UUID.randomUUID().toString();

            Response createResponse = postApiClient.createPost(config.projectId(), Map.of(
                    "title", "공지 알림 API 테스트 " + uniqueSuffix,
                    "content", "신규 공지 작성 알림을 검증합니다.",
                    "isNotice", true,
                    "attachments", List.of()
            ));
            long postId = createResponse.then()
                    .statusCode(201)
                    .body("code", equalTo("COMMON201"))
                    .body("result.projectId", equalTo((int) config.projectId()))
                    .body("result.isNotice", equalTo(true))
                    .extract()
                    .jsonPath()
                    .getLong("result.postId");

            try {
                // 실행 및 검증
                await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                    Response notificationsResponse = notificationApiClient.getNotifications(0, 100);
                    notificationsResponse.then()
                            .statusCode(200)
                            .body("code", equalTo("NOTI001"));

                    List<Map<String, Object>> notifications = notificationsResponse.jsonPath()
                            .getList("result.content");
                    assertThat(notifications).anySatisfy(notification -> {
                        assertThat(notification.get("type")).isEqualTo("NOTICE");
                        assertThat(((Number) notification.get("projectId")).longValue())
                                .isEqualTo(config.projectId());
                        assertThat(((Number) notification.get("resourceId")).longValue())
                                .isEqualTo(postId);
                        assertThat(notification.get("content")).isEqualTo("새 공지가 등록되었습니다.");
                    });
                });
            } finally {
                // 마무리 정리
                postApiClient.deletePost(config.projectId(), postId).then()
                        .statusCode(200)
                        .body("code", equalTo("COMMON200"))
                        .body("result.deleted", equalTo(true));
            }
        }
    }
}
