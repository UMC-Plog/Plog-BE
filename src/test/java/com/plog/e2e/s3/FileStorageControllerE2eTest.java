package com.plog.e2e.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.plog.e2e.support.E2eTestBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

@DisplayName("FileStorageController E2E")
class FileStorageControllerE2eTest extends E2eTestBase {

    @Nested
    @DisplayName("POST /api/files/presigned-upload-url")
    class CreatePresignedUploadUrl {

        @Test
        @DisplayName("인증된 사용자에게 단건 Presigned Upload 계약을 반환한다")
        void success() {
            Long userId = saveUser("file-upload");

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.POST,
                    uploadPath(),
                    userId,
                    Map.of(
                            "fileName", "meeting-notes.pdf",
                            "contentType", "application/pdf",
                            "fileSize", 1024,
                            "usage", "POST"
                    )
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(code(response)).isEqualTo("COMMON201");
            assertThat(result(response).path("uploadUrl").asText())
                    .startsWith("https://storage.test/upload/");
            assertThat(result(response).path("fileKey").asText())
                    .startsWith("posts/users/" + userId + "/")
                    .endsWith("/meeting-notes.pdf");
            assertThat(result(response).path("fileId").asLong()).isPositive();
            assertThat(result(response).path("signedHeaders").path("content-type").get(0).asText())
                    .isEqualTo("application/pdf");
            assertThat(result(response).path("signedHeaders").path("x-amz-tagging").get(0).asText())
                    .isEqualTo("state=pending&ownerId=" + userId);
            assertThat(result(response).path("expiresAt").asText()).isNotBlank();

            // 발급 시점에 PENDING 레지스트리 행이 생긴다. 이 행이 없으면 첨부 등록이 거부된다.
            assertThat(jdbc.queryForObject(
                    "select status from uploaded_file where file_key = ?", String.class,
                    result(response).path("fileKey").asText()))
                    .isEqualTo("PENDING");
        }

        @Test
        @DisplayName("확장자·Content-Type·크기·인증 정책을 검증한다")
        void policies() {
            Long userId = saveUser("file-policy");

            ResponseEntity<JsonNode> image = request(
                    HttpMethod.POST,
                    uploadPath(),
                    userId,
                    Map.of("fileName", "photo.png", "contentType", "image/png", "fileSize", 1024, "usage", "POST")
            );
            assertThat(image.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ResponseEntity<JsonNode> unsupported = request(
                    HttpMethod.POST,
                    uploadPath(),
                    userId,
                    Map.of("fileName", "logo.svg", "contentType", "image/svg+xml", "fileSize", 1024, "usage", "POST")
            );
            assertThat(code(unsupported)).isEqualTo("UNSUPPORTED_ATTACHMENT_TYPE");

            ResponseEntity<JsonNode> oversized = request(
                    HttpMethod.POST,
                    uploadPath(),
                    userId,
                    Map.of(
                            "fileName", "archive.zip",
                            "contentType", "application/zip",
                            "fileSize", 50L * 1024 * 1024 + 1,
                            "usage", "POST"
                    )
            );
            assertThat(code(oversized)).isEqualTo("FILE_SIZE_EXCEEDED");

            ResponseEntity<JsonNode> unauthorized = request(
                    HttpMethod.POST,
                    uploadPath(),
                    null,
                    Map.of("fileName", "notes.pdf", "contentType", "application/pdf", "fileSize", 1024, "usage", "POST")
            );
            assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(code(unauthorized)).isEqualTo("UNAUTHORIZED");
        }
    }

    @Nested
    @DisplayName("게시글 첨부 파일 생명주기")
    class PostAttachmentLifecycle {

        @Test
        @DisplayName("업로드 파일을 HEAD 검증하고 CONFIRMED 로 전이시킨다")
        void verifyAndConfirm() {
            Long userId = saveUser("file-post");
            Long projectId = saveProject("file-post");
            saveMember(userId, projectId, "MEMBER", "ACTIVE", "파일 작성자");
            String fileKey = "posts/users/" + userId + "/file-id/meeting-notes.pdf";
            savePendingFile(fileKey, userId, "POST", "meeting-notes.pdf", "application/pdf", 1024L);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.POST,
                    "/api/projects/" + projectId + "/posts",
                    userId,
                    Map.of(
                            "content", "파일을 공유합니다.",
                            "isNotice", false,
                            "attachments", List.of(Map.of(
                                    "attachmentType", "FILE",
                                    "fileName", "meeting-notes.pdf",
                                    "fileSize", 1024,
                                    "fileKey", fileKey
                            ))
                    )
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            // presigned 는 더 이상 생성 응답에 담기지 않는다. 클릭 시점에 발급 API 로 받아간다.
            assertThat(result(response).path("attachments").get(0).path("downloadUrlApi").asText())
                    .endsWith("/download-url");
            verify(s3Client).headObject(any(HeadObjectRequest.class));
            // 생성 시점에 presign 이 일어나지 않는 것이 이 변경의 목적이다.
            verifyNoInteractions(s3Presigner);
            assertThat(statusOf(fileKey)).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("같은 fileKey 를 두 게시글에 첨부하면 두 번째가 409 로 거부된다")
        void rejectsDuplicateReference() {
            Long userId = saveUser("file-dup");
            Long projectId = saveProject("file-dup");
            saveMember(userId, projectId, "MEMBER", "ACTIVE", "중복 시도자");
            String fileKey = "posts/users/" + userId + "/file-id/dup.pdf";
            savePendingFile(fileKey, userId, "POST", "dup.pdf", "application/pdf", 1024L);

            assertThat(createPostWith(projectId, userId, fileKey).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);

            ResponseEntity<JsonNode> second = createPostWith(projectId, userId, fileKey);

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(code(second)).isEqualTo("FILE_ALREADY_ATTACHED");
        }

        @Test
        @DisplayName("게시글 삭제 커밋 후 참조 파일을 ORPHANED 로 해제한다")
        void releaseAfterCommit() {
            Long userId = saveUser("file-delete");
            Long projectId = saveProject("file-delete");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "삭제자");
            Long postId = savePost(memberId, "삭제 대상", false);
            String fileKey = "posts/users/" + userId + "/file-id/delete.pdf";
            savePendingFile(fileKey, userId, "POST", "delete.pdf", "application/pdf", 1024L);
            jdbc.update("update uploaded_file set status = 'CONFIRMED', confirmed_at = now() "
                    + "where file_key = ?", fileKey);
            Long fileId = jdbc.queryForObject(
                    "select uploaded_file_id from uploaded_file where file_key = ?", Long.class, fileKey);
            jdbc.update("""
                    insert into post_attachments (
                        post_id, attachment_type, file_size, file_id, created_at, updated_at
                    ) values (?, 'FILE', 1024, ?, now(), now())
                    """, postId, fileId);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.DELETE,
                    "/api/projects/" + projectId + "/posts/" + postId,
                    userId,
                    null
            );

            assertThat(result(response).path("deleted").asBoolean()).isTrue();
            // 하드 삭제하지 않는다. 실제 제거는 S3 Lifecycle 이 orphaned 태그를 보고 처리한다.
            assertThat(statusOf(fileKey)).isEqualTo("ORPHANED");
        }

        private ResponseEntity<JsonNode> createPostWith(Long projectId, Long userId, String fileKey) {
            return request(HttpMethod.POST, "/api/projects/" + projectId + "/posts", userId,
                    Map.of(
                            "content", "파일을 공유합니다.",
                            "isNotice", false,
                            "attachments", List.of(Map.of(
                                    "attachmentType", "FILE",
                                    "fileName", "dup.pdf",
                                    "fileSize", 1024,
                                    "fileKey", fileKey
                            ))
                    ));
        }
    }

    /** presigned 발급이 만드는 PENDING 행을 테스트에서 직접 심는다. */
    private void savePendingFile(String fileKey, Long ownerId, String purpose,
                                 String fileName, String contentType, Long size) {
        jdbc.update("""
                insert into uploaded_file (
                    file_key, owner_id, purpose, original_filename, content_type, size,
                    status, issued_at, tagged_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 'PENDING', now(), now(), now(), now())
                """, fileKey, ownerId, purpose, fileName, contentType, size);
    }

    private String statusOf(String fileKey) {
        return jdbc.queryForObject(
                "select status from uploaded_file where file_key = ?", String.class, fileKey);
    }

    private String uploadPath() {
        return "/api/files/presigned-upload-url";
    }
}
