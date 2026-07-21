package com.plog.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

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

@DisplayName("PostController E2E")
class PostControllerE2eTest extends E2eTestBase {

    @Nested
    @DisplayName("POST /projects/{projectId}/posts")
    class CreatePost {

        @Test
        @DisplayName("ACTIVE 멤버가 게시글을 작성하면 201과 저장된 게시글을 반환한다")
        void success() {
            Long userId = saveUser("create-post");
            Long projectId = saveProject("create-post");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "곰곰");

            ResponseEntity<JsonNode> response = request(HttpMethod.POST, posts(projectId), userId, Map.of(
                    "content", "  회의록을 공유합니다.  ",
                    "isNotice", false,
                    "attachments", List.of(Map.of(
                            "attachmentType", "LINK",
                            "fileUrl", "https://docs.example.com/output"
                    ))
            ));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(code(response)).isEqualTo("COMMON201");
            assertThat(result(response).path("content").asText()).isEqualTo("회의록을 공유합니다.");
            assertThat(result(response).path("projectMemberId").asLong()).isEqualTo(memberId);
            assertThat(result(response).path("attachments")).hasSize(1);
            assertThat(count("posts", "post_id", result(response).path("postId").asLong())).isEqualTo(1L);
        }

        @Test
        @DisplayName("인증·ACTIVE 멤버·본문 정책을 적용한다")
        void policies() {
            Long activeUserId = saveUser("create-active");
            Long exitedUserId = saveUser("create-exited");
            Long projectId = saveProject("create-policy");
            saveMember(activeUserId, projectId, "MEMBER", "ACTIVE", "활성");
            saveMember(exitedUserId, projectId, "MEMBER", "EXIT", "탈퇴");

            ResponseEntity<JsonNode> unauthorized = request(
                    HttpMethod.POST, posts(projectId), null, Map.of("content", "본문", "isNotice", false));
            assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(code(unauthorized)).isEqualTo("UNAUTHORIZED");

            ResponseEntity<JsonNode> exited = request(
                    HttpMethod.POST, posts(projectId), exitedUserId, Map.of("content", "본문", "isNotice", false));
            assertThat(exited.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(code(exited)).isEqualTo("PROJECT_MEMBER_REQUIRED");

            ResponseEntity<JsonNode> invalid = request(
                    HttpMethod.POST, posts(projectId), activeUserId, Map.of("content", "   ", "isNotice", false));
            assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(code(invalid)).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Nested
    @DisplayName("GET /projects/{projectId}/posts")
    class GetFeed {

        @Test
        @DisplayName("공지와 일반 게시글을 분리하고 불투명 커서로 페이지를 조회한다")
        void noticeAndCursorPagination() {
            Long userId = saveUser("feed-reader");
            Long projectId = saveProject("feed-page");
            Long memberId = saveMember(userId, projectId, "OWNER", "ACTIVE", "바나나");
            Long noticeId = savePost(memberId, "고정 공지", true);
            Long firstId = savePost(memberId, "첫 번째", false);
            Long secondId = savePost(memberId, "두 번째", false);
            Long thirdId = savePost(memberId, "세 번째", false);
            jdbc.update("update posts set created_at = now() - interval '3 minutes' where post_id = ?", firstId);
            jdbc.update("update posts set created_at = now() - interval '2 minutes' where post_id = ?", secondId);
            jdbc.update("update posts set created_at = now() - interval '1 minute' where post_id = ?", thirdId);

            ResponseEntity<JsonNode> firstPage = request(
                    HttpMethod.GET, posts(projectId) + "?size=2", userId, null);

            assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(firstPage).path("notice").path("postId").asLong()).isEqualTo(noticeId);
            assertThat(result(firstPage).path("posts")).hasSize(2);
            assertThat(result(firstPage).path("posts").get(0).path("postId").asLong()).isEqualTo(thirdId);
            assertThat(result(firstPage).path("posts").get(1).path("postId").asLong()).isEqualTo(secondId);
            assertThat(result(firstPage).path("hasNext").asBoolean()).isTrue();
            String cursor = result(firstPage).path("nextCursor").asText();

            ResponseEntity<JsonNode> secondPage = request(
                    HttpMethod.GET, posts(projectId) + "?size=2&cursor=" + cursor, userId, null);
            assertThat(result(secondPage).path("posts")).hasSize(1);
            assertThat(result(secondPage).path("posts").get(0).path("postId").asLong()).isEqualTo(firstId);
            assertThat(result(secondPage).path("hasNext").asBoolean()).isFalse();
        }
    }

    @Nested
    @DisplayName("GET /projects/{projectId}/posts/{postId}")
    class GetPost {

        @Test
        @DisplayName("게시글 상세에 작성자·첨부·댓글·좋아요 집계를 포함한다")
        void success() {
            Long authorId = saveUser("detail-author");
            Long viewerId = saveUser("detail-viewer");
            Long projectId = saveProject("detail");
            Long authorMemberId = saveMember(authorId, projectId, "MEMBER", "ACTIVE", "작성자");
            Long viewerMemberId = saveMember(viewerId, projectId, "MEMBER", "ACTIVE", "조회자");
            Long postId = savePost(authorMemberId, "상세 본문", false);
            saveComment(postId, viewerMemberId, "댓글");
            jdbc.update("""
                    insert into likes (post_id, project_member_id, created_at, updated_at)
                    values (?, ?, now(), now())
                    """, postId, viewerMemberId);
            jdbc.update("""
                    insert into post_attachments (
                        post_id, attachment_type, file_url, created_at, updated_at
                    ) values (?, 'LINK', 'https://docs.example.com/detail', now(), now())
                    """, postId);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.GET, post(projectId, postId), viewerId, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(response).path("authorNickname").asText()).isEqualTo("작성자");
            assertThat(result(response).path("likeCount").asLong()).isEqualTo(1L);
            assertThat(result(response).path("commentCount").asLong()).isEqualTo(1L);
            assertThat(result(response).path("likedByMe").asBoolean()).isTrue();
            assertThat(result(response).path("attachments")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("PATCH /projects/{projectId}/posts/{postId}")
    class UpdatePost {

        @Test
        @DisplayName("생략한 첨부는 유지하고 전달한 첨부 배열은 전체 교체한다")
        void patchSemantics() {
            Long userId = saveUser("update-author");
            Long projectId = saveProject("update-post");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "작성자");
            Long postId = savePost(memberId, "기존 본문", false);
            jdbc.update("""
                    insert into post_attachments (
                        post_id, attachment_type, file_url, created_at, updated_at
                    ) values (?, 'LINK', 'https://docs.example.com/original', now(), now())
                    """, postId);

            ResponseEntity<JsonNode> contentOnly = request(
                    HttpMethod.PATCH, post(projectId, postId), userId, Map.of("content", "  수정 본문  "));
            assertThat(result(contentOnly).path("content").asText()).isEqualTo("수정 본문");
            assertThat(result(contentOnly).path("attachments")).hasSize(1);

            ResponseEntity<JsonNode> replacement = request(
                    HttpMethod.PATCH, post(projectId, postId), userId, Map.of("attachments", List.of()));
            assertThat(result(replacement).path("attachments")).isEmpty();
            assertThat(count("post_attachments", "post_id", postId)).isZero();
        }

        @Test
        @DisplayName("게시글 작성자가 아니면 수정을 거부한다")
        void authorOnly() {
            Long authorId = saveUser("update-owner");
            Long otherId = saveUser("update-other");
            Long projectId = saveProject("update-policy");
            Long authorMemberId = saveMember(authorId, projectId, "MEMBER", "ACTIVE", "작성자");
            saveMember(otherId, projectId, "MEMBER", "ACTIVE", "다른 멤버");
            Long postId = savePost(authorMemberId, "본문", false);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.PATCH, post(projectId, postId), otherId, Map.of("content", "수정 시도"));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(code(response)).isEqualTo("POST_UPDATE_PERMISSION_DENIED");
        }
    }

    @Nested
    @DisplayName("DELETE /projects/{projectId}/posts/{postId}")
    class DeletePost {

        @Test
        @DisplayName("게시글과 첨부·댓글·좋아요를 물리 삭제한다")
        void physicalCascadeDelete() {
            Long userId = saveUser("delete-author");
            Long projectId = saveProject("delete-post");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "작성자");
            Long postId = savePost(memberId, "삭제 대상", false);
            Long commentId = saveComment(postId, memberId, "삭제될 댓글");
            jdbc.update("""
                    insert into likes (post_id, project_member_id, created_at, updated_at)
                    values (?, ?, now(), now())
                    """, postId, memberId);
            jdbc.update("""
                    insert into post_attachments (
                        post_id, attachment_type, file_url, created_at, updated_at
                    ) values (?, 'LINK', 'https://docs.example.com/delete', now(), now())
                    """, postId);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.DELETE, post(projectId, postId), userId, null);

            assertThat(result(response).path("deleted").asBoolean()).isTrue();
            assertThat(count("posts", "post_id", postId)).isZero();
            assertThat(count("comments", "comment_id", commentId)).isZero();
            assertThat(count("likes", "post_id", postId)).isZero();
            assertThat(count("post_attachments", "post_id", postId)).isZero();
        }
    }

    @Nested
    @DisplayName("PATCH /projects/{projectId}/posts/{postId}/notice")
    class ChangeNotice {

        @Test
        @DisplayName("OWNER가 새 공지를 지정하면 기존 공지를 해제한다")
        void replacesSingleNotice() {
            Long ownerId = saveUser("notice-owner");
            Long projectId = saveProject("notice");
            Long ownerMemberId = saveMember(ownerId, projectId, "OWNER", "ACTIVE", "오너");
            Long oldNoticeId = savePost(ownerMemberId, "기존 공지", true);
            Long nextNoticeId = savePost(ownerMemberId, "새 공지", false);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.PATCH,
                    post(projectId, nextNoticeId) + "/notice",
                    ownerId,
                    Map.of("isNotice", true)
            );
            assertThat(result(response).path("isNotice").asBoolean()).isTrue();
            assertThat(jdbc.queryForObject(
                    "select is_notice from posts where post_id = ?", Boolean.class, oldNoticeId)).isFalse();
            assertThat(jdbc.queryForObject(
                    "select count(*) from posts where is_notice = true and project_member_id = ?",
                    Long.class, ownerMemberId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("일반 멤버는 공지를 변경할 수 없다")
        void ownerOnly() {
            Long userId = saveUser("notice-member");
            Long projectId = saveProject("notice-policy");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "멤버");
            Long postId = savePost(memberId, "공지 시도", false);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.PATCH,
                    post(projectId, postId) + "/notice",
                    userId,
                    Map.of("isNotice", true)
            );
            assertThat(code(response)).isEqualTo("NOTICE_PERMISSION_DENIED");
        }
    }

    @Nested
    @DisplayName("POST /projects/{projectId}/posts/{postId}/comments")
    class CreateComment {

        @Test
        @DisplayName("ACTIVE 멤버가 댓글을 작성하면 trim된 내용과 201을 반환한다")
        void success() {
            Long userId = saveUser("comment-create");
            Long projectId = saveProject("comment-create");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "댓글 작성자");
            Long postId = savePost(memberId, "댓글 대상", false);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.POST,
                    post(projectId, postId) + "/comments",
                    userId,
                    Map.of("content", "  잘 봤습니다  ")
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result(response).path("content").asText()).isEqualTo("잘 봤습니다");
        }
    }

    @Nested
    @DisplayName("GET /projects/{projectId}/posts/{postId}/comments")
    class GetComments {

        @Test
        @DisplayName("댓글을 작성 시간 오름차순으로 반환한다")
        void orderedOldestFirst() {
            Long userId = saveUser("comment-list");
            Long projectId = saveProject("comment-list");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "조회자");
            Long postId = savePost(memberId, "댓글 대상", false);
            Long firstId = saveComment(postId, memberId, "첫 댓글");
            Long secondId = saveComment(postId, memberId, "두 번째 댓글");
            jdbc.update("update comments set created_at = now() - interval '1 minute' where comment_id = ?", firstId);

            ResponseEntity<JsonNode> response = request(
                    HttpMethod.GET, post(projectId, postId) + "/comments", userId, null);
            assertThat(result(response).path("comments")).hasSize(2);
            assertThat(result(response).path("comments").get(0).path("commentId").asLong()).isEqualTo(firstId);
            assertThat(result(response).path("comments").get(1).path("commentId").asLong()).isEqualTo(secondId);
        }
    }

    @Nested
    @DisplayName("DELETE /projects/{projectId}/posts/{postId}/comments/{commentId}")
    class DeleteComment {

        @Test
        @DisplayName("다른 멤버는 거부하고 OWNER는 댓글을 물리 삭제할 수 있다")
        void authorOrOwnerOnly() {
            Long authorId = saveUser("comment-author");
            Long otherId = saveUser("comment-other");
            Long ownerId = saveUser("comment-owner");
            Long projectId = saveProject("comment-delete");
            Long authorMemberId = saveMember(authorId, projectId, "MEMBER", "ACTIVE", "작성자");
            saveMember(otherId, projectId, "MEMBER", "ACTIVE", "다른 멤버");
            saveMember(ownerId, projectId, "OWNER", "ACTIVE", "오너");
            Long postId = savePost(authorMemberId, "댓글 대상", false);
            Long commentId = saveComment(postId, authorMemberId, "삭제 대상");
            String path = post(projectId, postId) + "/comments/" + commentId;

            ResponseEntity<JsonNode> denied = request(HttpMethod.DELETE, path, otherId, null);
            assertThat(code(denied)).isEqualTo("COMMENT_DELETE_PERMISSION_DENIED");

            ResponseEntity<JsonNode> deleted = request(HttpMethod.DELETE, path, ownerId, null);
            assertThat(result(deleted).path("deleted").asBoolean()).isTrue();
            assertThat(count("comments", "comment_id", commentId)).isZero();
        }
    }

    @Nested
    @DisplayName("PUT /projects/{projectId}/posts/{postId}/like")
    class LikePost {

        @Test
        @DisplayName("중복 좋아요 요청을 멱등적으로 처리한다")
        void idempotent() {
            Long userId = saveUser("like-put");
            Long projectId = saveProject("like-put");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "좋아요");
            Long postId = savePost(memberId, "좋아요 대상", false);
            String path = post(projectId, postId) + "/like";

            ResponseEntity<JsonNode> first = request(HttpMethod.PUT, path, userId, null);
            ResponseEntity<JsonNode> duplicate = request(HttpMethod.PUT, path, userId, null);
            assertThat(result(first).path("liked").asBoolean()).isTrue();
            assertThat(result(duplicate).path("likeCount").asLong()).isEqualTo(1L);
            assertThat(count("likes", "post_id", postId)).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("DELETE /projects/{projectId}/posts/{postId}/like")
    class UnlikePost {

        @Test
        @DisplayName("좋아요가 없어도 취소 요청을 멱등적으로 처리한다")
        void idempotent() {
            Long userId = saveUser("like-delete");
            Long projectId = saveProject("like-delete");
            Long memberId = saveMember(userId, projectId, "MEMBER", "ACTIVE", "좋아요");
            Long postId = savePost(memberId, "좋아요 대상", false);
            jdbc.update("""
                    insert into likes (post_id, project_member_id, created_at, updated_at)
                    values (?, ?, now(), now())
                    """, postId, memberId);
            String path = post(projectId, postId) + "/like";

            ResponseEntity<JsonNode> first = request(HttpMethod.DELETE, path, userId, null);
            ResponseEntity<JsonNode> duplicate = request(HttpMethod.DELETE, path, userId, null);
            assertThat(result(first).path("liked").asBoolean()).isFalse();
            assertThat(result(duplicate).path("likeCount").asLong()).isZero();
            assertThat(count("likes", "post_id", postId)).isZero();
        }
    }

    private long count(String table, String column, Long id) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Long.class, id);
    }

    private String posts(Long projectId) {
        return "/projects/" + projectId + "/posts";
    }

    private String post(Long projectId, Long postId) {
        return posts(projectId) + "/" + postId;
    }
}
