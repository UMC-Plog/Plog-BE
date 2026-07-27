package com.plog.domain.post.controller.docs;

import com.plog.domain.post.dto.CommentDto;
import com.plog.domain.post.dto.PostDto;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Post", description = "프로젝트 게시글 · 댓글 · 좋아요 (인증 및 활성 멤버 필요)")
public interface PostControllerDoc {

    @Operation(
            summary = "게시글 작성",
            description = """
                    본문은 1~5000자이며 앞뒤 공백은 제거되어 저장됩니다.

                    **공지(`isNotice=true`)는 OWNER만 지정할 수 있고, 프로젝트당 1개입니다.**
                    새 공지를 올리면 기존 공지는 자동으로 해제됩니다.

                    ### 첨부 (최대 10개)
                    - `FILE` — 먼저 `POST /files/presigned-upload-url`로 발급받아 S3에 업로드한 뒤
                      받은 `fileKey`를 보냅니다. `fileName`·`fileSize`는 발급 때와 같아야 합니다.
                    - `LINK` — `fileUrl`에 외부 링크. **https만 허용**하며 사설망·localhost는 거부합니다.

                    한 파일은 한 곳에만 첨부할 수 있어, 이미 쓰인 `fileKey`를 보내면 409입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "작성 성공 (COMMON201)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "본문 길이·첨부 형식 오류 (VALIDATION_ERROR) "
                            + "또는 https가 아닌 링크 (INVALID_LINK_URL)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요 (COMMON401)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 멤버 아님 (PROJECT_MEMBER_REQUIRED) "
                            + "또는 OWNER가 아닌데 공지 지정 (NOTICE_PERMISSION_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 없음 (PROJECT_NOT_FOUND)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 다른 첨부에 사용된 파일 (FILE_ALREADY_ATTACHED)")
    })
    ResponseEntity<ApiResponse<PostDto.CreateResponse>> createPost(
            Long projectId, Long userId, @Valid PostDto.CreateRequest request);

    @Operation(
            summary = "게시글 목록 (피드)",
            description = """
                    최신순 커서 페이지네이션입니다.

                    - `size` 기본 20, **최대 50**(초과 요청은 50으로 잘림). 0 이하는 400.
                    - `cursor`는 이전 응답의 `nextCursor`를 그대로 넘깁니다. 내용은 해석하지 마세요(불투명 문자열).
                      `hasNext=false`면 `nextCursor`는 null입니다.
                    - 공지는 목록과 별개로 `notice` 필드에 항상 함께 내려갑니다. 없으면 null입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "size가 0 이하 (VALIDATION_ERROR) "
                            + "또는 커서 형식 오류 (INVALID_CURSOR)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 멤버 아님 (PROJECT_MEMBER_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 없음 (PROJECT_NOT_FOUND)")
    })
    ApiResponse<PostDto.FeedResponse> getFeed(Long projectId, Long userId, String cursor, int size);

    @Operation(
            summary = "게시글 상세",
            description = """
                    첨부·좋아요 수·댓글 수·본인 좋아요 여부를 함께 반환합니다.

                    첨부의 `fileUrl`은 **10분간 유효한 임시 다운로드 URL**입니다. 오래 보관하지 말고
                    필요할 때 다시 조회하세요. `FILE` 첨부에는 `fileId`가 함께 오며, 수정 요청에서
                    그 첨부를 유지할 때 이 값을 씁니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 멤버 아님 (PROJECT_MEMBER_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음 "
                            + "(PROJECT_NOT_FOUND / POST_NOT_FOUND)")
    })
    ApiResponse<PostDto.Response> getPost(Long projectId, Long postId, Long userId);

    @Operation(
            summary = "게시글 수정",
            description = """
                    **작성자 본인만** 수정할 수 있습니다. 보낸 필드만 반영됩니다.

                    ### 첨부 처리 — 부분 수정이 아니라 전체 교체입니다
                    - `attachments`를 **생략하면** 기존 첨부가 그대로 유지됩니다.
                    - `attachments`를 **보내면 그 배열이 최종 상태**가 됩니다. 빠진 첨부는 삭제됩니다.
                      (빈 배열을 보내면 전부 삭제)

                    ### 유지할 첨부와 새 첨부의 구분
                    - **유지**: 조회 응답에서 받은 `fileId`를 보냅니다.
                    - **추가**: 새로 발급받은 `fileKey`를 보냅니다.

                    한 첨부에 `fileId`와 `fileKey`를 **같이 보내면 400**입니다. 다른 게시글의
                    `fileId`를 보내도 400입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "본문 길이·첨부 형식 오류, fileId/fileKey 동시 전송, "
                            + "타 게시글의 fileId (VALIDATION_ERROR) / https가 아닌 링크 (INVALID_LINK_URL)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "작성자가 아님 (POST_UPDATE_PERMISSION_DENIED) "
                            + "또는 활성 멤버 아님 (PROJECT_MEMBER_REQUIRED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 다른 첨부에 사용된 파일 (FILE_ALREADY_ATTACHED)")
    })
    ApiResponse<PostDto.UpdateResponse> updatePost(
            Long projectId, Long postId, Long userId, @Valid PostDto.UpdateRequest request);

    @Operation(
            summary = "게시글 삭제",
            description = """
                    **작성자 본인 또는 OWNER**만 삭제할 수 있습니다. 댓글·좋아요·첨부가 함께 지워집니다.

                    첨부 파일은 즉시 삭제되지 않고 사용 안 함 상태로 표시되며, 유예 기간 후
                    저장소에서 회수됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "작성자도 OWNER도 아님 (POST_DELETE_PERMISSION_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음")
    })
    ApiResponse<PostDto.DeletedResponse> deletePost(Long projectId, Long postId, Long userId);

    @Operation(
            summary = "공지 지정 / 해제",
            description = """
                    **OWNER만** 변경할 수 있습니다. 공지는 프로젝트당 1개이므로,
                    `isNotice=true`로 지정하면 **기존 공지는 자동으로 해제**됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "OWNER 아님 (NOTICE_PERMISSION_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음")
    })
    ApiResponse<PostDto.NoticeResponse> changeNotice(
            Long projectId, Long postId, Long userId, @Valid PostDto.NoticeRequest request);

    @Operation(
            summary = "댓글 작성",
            description = "1~1000자. 앞뒤 공백은 제거되어 저장됩니다. 대댓글은 지원하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "작성 성공 (COMMON201)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "본문이 비었거나 1000자 초과 (VALIDATION_ERROR)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음")
    })
    ResponseEntity<ApiResponse<CommentDto.Response>> createComment(
            Long projectId, Long postId, Long userId, @Valid CommentDto.CreateRequest request);

    @Operation(
            summary = "댓글 목록",
            description = "작성 시간 오름차순 전체 조회입니다. 페이지네이션이 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음")
    })
    ApiResponse<CommentDto.ListResponse> getComments(Long projectId, Long postId, Long userId);

    @Operation(
            summary = "댓글 삭제",
            description = "**작성자 본인 또는 OWNER**만 삭제할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "작성자도 OWNER도 아님 (COMMENT_DELETE_PERMISSION_DENIED)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "게시글 또는 댓글 없음 (POST_NOT_FOUND / COMMENT_NOT_FOUND)")
    })
    ApiResponse<PostDto.DeletedResponse> deleteComment(
            Long projectId, Long postId, Long commentId, Long userId);

    @Operation(
            summary = "좋아요",
            description = """
                    **멱등입니다.** 이미 누른 상태에서 다시 호출해도 오류 없이 현재 상태를 반환합니다.
                    응답의 `likeCount`는 호출 직후의 최신 값입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 (이미 누른 경우 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음")
    })
    ApiResponse<PostDto.LikeResponse> like(Long projectId, Long postId, Long userId);

    @Operation(
            summary = "좋아요 취소",
            description = "**멱등입니다.** 누르지 않은 상태에서 호출해도 오류 없이 현재 상태를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "성공 (누르지 않은 경우 포함)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "프로젝트 또는 게시글 없음")
    })
    ApiResponse<PostDto.LikeResponse> unlike(Long projectId, Long postId, Long userId);
}
