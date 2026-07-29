package com.plog.domain.task.controller.docs;

import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.AttachmentDownloadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Task", description = "업무카드 API")
public interface TaskAttachmentControllerDoc {

    @Operation(
            summary = "업무카드 첨부 다운로드 URL 발급",
            description = """
                    FILE 첨부의 S3 presigned 다운로드 URL 을 발급합니다. **첨부를 클릭하는 시점에**
                    호출하세요.
                    - 목록·상세 응답에는 이 URL 이 들어 있지 않습니다. 미리 발급하면 사용자가
                      클릭할 때쯤 만료돼 다운로드가 실패하기 때문입니다.
                    - 응답의 `downloadUrl` 로 이동하면 S3 에서 바로 내려받습니다. 유효 시간은 5분입니다.
                    - 목록 응답의 `downloadUrlApi` 에 이 엔드포인트의 절대 URL 이 들어 있으니
                      그대로 호출하면 됩니다. **이 주소를 `<a href>` 에 걸면 JSON 이 보입니다.**
                    - LINK 첨부는 목록 응답의 `linkUrl` 로 바로 이동하세요. 이 API 는 400 을 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "LINK 첨부에 발급을 요청함 (TASK005)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "프로젝트 활성 멤버가 아님 (PROJECT_MEMBER_REQUIRED)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "첨부가 없거나 해당 프로젝트 소속이 아님 (TASK008)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<AttachmentDownloadResponse>> createDownloadUrl(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "업무카드 첨부 ID", example = "3") Long taskAttachmentId,
            Long userId
    );
}
