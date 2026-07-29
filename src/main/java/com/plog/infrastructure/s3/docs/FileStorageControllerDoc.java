package com.plog.infrastructure.s3.docs;

import com.plog.global.api.response.ApiResponse;
import com.plog.infrastructure.s3.FileStorageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "File Storage", description = "첨부파일 업로드용 Presigned URL 발급 (인증 필요)")
public interface FileStorageControllerDoc {

    @Operation(
            summary = "Presigned 업로드 URL 발급",
            description = """
                    S3에 직접 업로드할 수 있는 URL을 발급하고, 서버에 해당 파일의 대기(PENDING) 기록을 남깁니다.
                    발급 시점에는 아직 파일이 업로드되지 않은 상태입니다.

                    ### 파일 첨부는 3단계입니다
                    **1단계만 하고 끝내면 파일은 자동 삭제됩니다.**

                    1. `POST /api/files/presigned-upload-url` — 이 API. `uploadUrl` / `fileKey` / `fileId`를 받습니다.
                    2. `PUT {uploadUrl}` — 프론트가 S3로 **직접** 업로드. 응답의 `signedHeaders`를 그대로 실어야 합니다.
                    3. 도메인 API에 `fileKey` 전달 — 게시글/업무카드 생성, 채팅 메시지 전송 등.

                    3단계까지 마쳐야 파일이 영구 보관됩니다. 1~2단계에서 멈춘 파일(업로드해두고 첨부하지 않은 경우)은
                    하루 뒤 S3 수명 주기 규칙이 삭제합니다.

                    ### 2단계 업로드 시 주의
                    응답의 `signedHeaders`에 담긴 헤더를 **하나도 빠뜨리지 말고** PUT 요청에 그대로 넣어야 합니다.
                    특히 `x-amz-tagging`이 서명에 포함되어 있어, 누락하면 S3가 서명 불일치로 403을 반환합니다.

                    ```
                    PUT {uploadUrl}
                    Content-Type: application/pdf
                    x-amz-tagging: state=pending&ownerId=7
                    (본문: 파일 바이너리)
                    ```

                    ### usage 값
                    파일이 쓰일 도메인입니다. S3 저장 경로와 다운로드 방식을 함께 결정하므로
                    **3단계에서 실제로 첨부할 도메인과 반드시 일치해야 합니다.**
                    (`CHAT`으로 발급한 파일을 게시글에 첨부하면 거부됩니다.)

                    | 값 | 용도 | 다운로드 동작 |
                    |---|---|---|
                    | `POST` | 게시글 첨부 | 클릭 시 발급 API 로 presigned 를 받아 내려받기 |
                    | `TASK` | 업무카드 산출물 | 클릭 시 발급 API 로 presigned 를 받아 내려받기 |
                    | `CHAT` | 채팅 첨부 | 백엔드 프록시가 바로 서빙(이미지 인라인) |

                    ### 허용 파일
                    - 확장자: `pdf` `pptx` `docx` `zip` `fig` `jpg` `jpeg` `png` `webp` `gif`
                    - `.fig`는 표준 MIME 타입이 없어 `contentType`에 `application/octet-stream`을 보내야 합니다.
                      (브라우저의 `file.type`이 빈 문자열이므로 프론트가 직접 채워야 합니다)
                    - 크기: 이미지 10MB / 그 외 50MB
                    - `contentType`은 확장자와 일치해야 합니다. (`photo.png` + `image/jpeg` → 거부)
                    - `svg`는 스크립트를 품을 수 있어 허용하지 않습니다.

                    ### 한 파일은 한 곳에만 첨부할 수 있습니다
                    같은 `fileKey`를 두 번째 리소스에 첨부하면 3단계에서 409(`FILE_ALREADY_ATTACHED`)로 거부됩니다.
                    같은 파일을 여러 곳에 붙이려면 각각 새로 발급받아 업로드하세요.

                    ### 응답의 fileId 용도
                    게시글 **수정** 시, 유지할 기존 첨부는 `fileKey`가 아니라 이 `fileId`를 보냅니다.
                    새로 추가하는 첨부만 `fileKey`를 씁니다. 한 첨부에 둘을 같이 보내면 400입니다.

                    ### 유효 기간
                    `uploadUrl`은 발급 후 **10분**간 유효합니다(`expiresAt` 참고). 만료되면 재발급받으세요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "발급 성공 (COMMON201)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 확장자·불일치 Content-Type(UNSUPPORTED_ATTACHMENT_TYPE) "
                            + "또는 크기 초과(FILE_SIZE_EXCEEDED)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "허용되지 않은 형식", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "UNSUPPORTED_ATTACHMENT_TYPE",
                                              "message": "지원하지 않는 파일 형식입니다."
                                            }
                                            """),
                                    @ExampleObject(name = "크기 초과", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "FILE_SIZE_EXCEEDED",
                                              "message": "파일 크기 제한을 초과했습니다. (문서 50MB, 이미지 10MB)"
                                            }
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 헤더가 없거나(COMMON401) 유효하지 않은 토큰(AUTH011)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "인증 헤더 없음", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "COMMON401",
                                              "message": "인증이 필요합니다."
                                            }
                                            """),
                                    @ExampleObject(name = "유효하지 않은 토큰", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH011",
                                              "message": "유효하지 않은 토큰입니다."
                                            }
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "파일 저장 기능 비활성화 (FILE_STORAGE_DISABLED)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "FILE_STORAGE_DISABLED",
                                      "message": "파일 저장 기능이 비활성화되어 있습니다."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<FileStorageDto.PresignedUploadResponse>> createPresignedUploadUrl(
            Long userId, @Valid FileStorageDto.PresignedUploadRequest request);
}
