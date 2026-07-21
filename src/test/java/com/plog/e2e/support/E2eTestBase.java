package com.plog.e2e.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.project.service.InviteTokenCipher;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.util.HashUtil;
import com.plog.infrastructure.fcm.FcmGateway;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2eTestBase {

    private static final String JWT_SECRET = "plog-e2e-test-jwt-secret-key-0123456789";
    private static final String INVITE_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.access-token-ttl", () -> "30m");
        registry.add("app.jwt.refresh-token-ttl", () -> "14d");
        registry.add("app.cors.allowed-origins[0]", () -> "http://localhost:3000");
        registry.add("plog.invite.encryption-key-base64", () -> INVITE_KEY);
        registry.add("plog.invite.base-url", () -> "https://plog.test/invite");
        registry.add("plog.s3.enabled", () -> "true");
        registry.add("plog.s3.bucket", () -> "plog-e2e");
        registry.add("plog.s3.region", () -> "ap-northeast-2");
        registry.add("plog.fcm.enabled", () -> "false");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "3025");
        registry.add("spring.mail.username", () -> "e2e");
        registry.add("spring.mail.password", () -> "e2e");
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    private InviteTokenCipher inviteTokenCipher;

    @MockitoBean
    protected S3Client s3Client;

    @MockitoBean
    protected S3Presigner s3Presigner;

    @MockitoBean
    protected FcmGateway fcmGateway;

    @BeforeEach
    void resetDatabaseAndExternalStubs() {
        jdbc.execute("TRUNCATE TABLE tb_user RESTART IDENTITY CASCADE");

        given(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder()
                        .contentLength(1024L)
                        .contentType("application/pdf")
                        .build());
        given(s3Presigner.presignPutObject(any(software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                .willAnswer(invocation -> {
                    var request = invocation.<software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest>getArgument(0);
                    var put = request.putObjectRequest();
                    PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
                    given(presigned.url()).willReturn(url("https://storage.test/upload/" + put.key()));
                    given(presigned.signedHeaders()).willReturn(Map.of(
                            "content-type", List.of(put.contentType()),
                            "x-amz-tagging", List.of(put.tagging())
                    ));
                    return presigned;
                });
        given(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willAnswer(invocation -> {
                    var request = invocation.<software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest>getArgument(0);
                    var get = request.getObjectRequest();
                    PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
                    given(presigned.url()).willReturn(url("https://storage.test/download/" + get.key()));
                    return presigned;
                });
    }

    protected Long saveUser(String suffix) {
        return jdbc.queryForObject("""
                insert into tb_user (email, password, name, nickname, created_at, updated_at)
                values (?, ?, ?, ?, now(), now())
                returning user_id
                """, Long.class,
                suffix + "@plog.test", "encoded-password", "User " + suffix, "nick-" + suffix);
    }

    protected Long saveProject(String suffix) {
        String rawToken = "invite-" + suffix;
        return jdbc.queryForObject("""
                insert into projects (
                    project_name, invite_token_hash, invite_token_encrypted,
                    project_type, status, start_day, end_day, created_at, updated_at
                ) values (?, ?, ?, 'DEVELOP', 'IN_PROGRESS', ?, ?, now(), now())
                returning project_id
                """, Long.class,
                "Plog " + suffix,
                HashUtil.sha256Hex(rawToken),
                inviteTokenCipher.encrypt(rawToken),
                LocalDate.now().minusDays(3),
                LocalDate.now().plusDays(30));
    }

    protected Long saveMember(Long userId, Long projectId, String role, String status, String nickname) {
        return jdbc.queryForObject("""
                insert into project_members (
                    user_id, project_id, role, project_status, an_nickname, created_at, updated_at
                ) values (?, ?, ?, ?, ?, now(), now())
                returning project_member_id
                """, Long.class, userId, projectId, role, status, nickname);
    }

    protected Long savePost(Long memberId, String content, boolean notice) {
        return jdbc.queryForObject("""
                insert into posts (project_member_id, content, is_notice, created_at, updated_at)
                values (?, ?, ?, now(), now())
                returning post_id
                """, Long.class, memberId, content, notice);
    }

    protected Long saveComment(Long postId, Long memberId, String content) {
        return jdbc.queryForObject("""
                insert into comments (post_id, project_member_id, content, created_at, updated_at)
                values (?, ?, ?, now(), now())
                returning comment_id
                """, Long.class, postId, memberId, content);
    }

    protected String bearer(Long userId) {
        return "Bearer " + jwtProvider.createAccessToken(userId);
    }

    protected ResponseEntity<JsonNode> request(
            HttpMethod method,
            String path,
            Long userId,
            Object body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userId != null) {
            headers.set(HttpHeaders.AUTHORIZATION, bearer(userId));
        }
        String json = body == null ? null : writeJson(body);
        return http.exchange(path, method, new HttpEntity<>(json, headers), JsonNode.class);
    }

    protected JsonNode result(ResponseEntity<JsonNode> response) {
        return response.getBody().path("result");
    }

    protected String code(ResponseEntity<JsonNode> response) {
        return response.getBody().path("code").asText();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("E2E request serialization failed", exception);
        }
    }

    private URL url(String value) {
        try {
            return URI.create(value).toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
