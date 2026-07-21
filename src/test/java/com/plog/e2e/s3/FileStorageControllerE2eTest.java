package com.plog.e2e.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@DisplayName("FileStorageController E2E")
class FileStorageControllerE2eTest extends E2eTestBase {

    @Nested
    @DisplayName("POST /files/presigned-upload-url")
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
                    .startsWith("temporary/post/users/" + userId + "/")
                    .endsWith("/meeting-notes.pdf");
            assertThat(result(response).path("signedHeaders").path("content-type").get(0).asText())
                    .isEqualTo("application/pdf");
            assertThat(result(response).path("signedHeaders").path("x-amz-tagging").get(0).asText())
                    .isEqualTo("state=temporary&ownerId=" + userId);
            assertThat(result(response).path("expiresAt").asText()).isNotBlank();
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
        @DisplayName("업로드 파일을 HEAD 검증하고 커밋 후 영구 파일로 승격한다")
        void verifyAndPromote() {
            Long userId = saveUser("file-post");
            Long projectId = saveProject("file-post");
            saveMember(userId, projectId, "MEMBER", "ACTIVE", "파일 작성자");
            String fileKey = "temporary/post/users/" + userId + "/file-id/meeting-notes.pdf";

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.POST,
                    "/projects/" + projectId + "/posts",
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
            assertThat(result(response).path("attachments").get(0).path("fileUrl").asText())
                    .isEqualTo("https://storage.test/download/" + fileKey);
            verify(s3Client).headObject(any(HeadObjectRequest.class));
            verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
            verify(s3Client, timeout(3_000)).putObjectTagging(any(PutObjectTaggingRequest.class));
        }

        @Test
        @DisplayName("게시글 삭제 커밋 후 참조 파일을 삭제한다")
        void deleteAfterCommit() {
            Long userId = saveUser("file-delete");
            Long projectId = saveProject("file-delete");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "삭제자");
            Long postId = savePost(memberId, "삭제 대상", false);
            String fileKey = "temporary/post/users/" + userId + "/file-id/delete.pdf";
            jdbc.update("""
                    insert into post_attachments (
                        post_id, attachment_type, file_name, file_size, file_url, created_at, updated_at
                    ) values (?, 'FILE', 'delete.pdf', 1024, ?, now(), now())
                    """, postId, fileKey);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.DELETE,
                    "/projects/" + projectId + "/posts/" + postId,
                    userId,
                    null
            );

            assertThat(result(response).path("deleted").asBoolean()).isTrue();
            verify(s3Client, timeout(3_000)).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    private String uploadPath() {
        return "/files/presigned-upload-url";
    }
}
