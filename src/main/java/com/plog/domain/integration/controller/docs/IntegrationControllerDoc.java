package com.plog.domain.integration.controller.docs;

import com.plog.domain.integration.dto.request.FigmaResourceRegisterRequest;
import com.plog.domain.integration.dto.request.GoogleResourceRegisterRequest;
import com.plog.domain.integration.dto.request.IntegrationActorMappingRequest;
import com.plog.domain.integration.dto.request.NotionResourceRegisterRequest;
import com.plog.domain.integration.dto.response.IntegrationActorMappingListResponse;
import com.plog.domain.integration.dto.response.IntegrationActorMappingResponse;
import com.plog.domain.integration.dto.response.IntegrationAuthorizationResponse;
import com.plog.domain.integration.dto.response.IntegrationCollectionResponse;
import com.plog.domain.integration.dto.response.IntegrationDisconnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceCandidateResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceListResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceResponse;
import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.view.RedirectView;

public interface IntegrationControllerDoc {

    @Operation(
            tags = "Integration",
            summary = "1. 프로젝트 외부 연동 상태 조회",
            description = """
                    현재 사용자가 ACTIVE 멤버인 프로젝트의 외부 provider 연동 상태를 조회합니다.
                    GITHUB, FIGMA, NOTION, GOOGLE 순서로 모두 내려주며, 아직 연결되지 않은 provider는 linked=false입니다.
                    토큰/secret은 응답에 포함하지 않고, 화면 표시용 connectedAccountName만 제공합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "외부 연동 상태 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "프로젝트 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<IntegrationStatusResponse>> getProjectIntegrations(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            Long userId
    );

    @Operation(
            tags = "Integration",
            summary = "2. 외부 계정 연동 URL 발급",
            description = """
                    프로젝트 ACTIVE 멤버가 provider 승인 화면으로 이동할 URL을 발급합니다.
                    이미 같은 프로젝트에 해당 provider가 연결되어 있으면 409를 반환합니다.
                    응답의 authorization 값을 프론트에서 새 창 또는 현재 창으로 이동시키면 provider 승인 플로우가 시작됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "연동 URL 발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "지원하지 않는 provider 또는 잘못된 요청",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 연동된 provider",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationAuthorizationResponse>> issueAuthorizationUrl(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "github") String provider,
            Long userId
    );

    @Operation(
            tags = "Integration",
            summary = "6. 외부 계정 연동 해제",
            description = """
                    프로젝트에 저장된 provider 연동 정보를 삭제합니다.
                    Plog에 저장된 연결 정보, 수집 대상, 수집 활동, 계정 매핑이 함께 정리됩니다.
                    provider 서비스 자체의 앱 설치 또는 OAuth 권한 철회는 각 provider 화면에서 별도로 처리할 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "외부 연동 해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "지원하지 않는 provider",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 또는 연동 정보 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationDisconnectionResponse>> disconnect(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "figma") String provider,
            Long userId
    );

    @Operation(tags = "Integration",
            summary = "3-1. 등록된 외부 연동 리소스 조회",
            description = """
                    프로젝트 ACTIVE 멤버가 provider에 등록된 수집 대상 리소스를 조회합니다.
                    GitHub repository는 GitHub App 설치 callback에서 자동 등록되며, 나머지 provider는 별도 등록 API를 호출해야 합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "등록 리소스 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 또는 provider 연동 정보 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationResourceListResponse>> getResources(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "figma") String provider,
            Long userId
    );

    @Operation(
            tags = "Integration",
            summary = "5-1. 프로젝트 멤버 외부 계정 매핑 조회",
            description = """
                    provider 활동을 최소 한 번 수집한 뒤 발견된 provider 계정과 프로젝트 멤버의 명시적 매핑을 조회합니다.
                    mappings에는 팀원이 직접 저장한 매핑, availableProviderActors에는 아직 선택되지 않은 provider 계정이 포함됩니다.
                    GitHub는 ID/login, Notion은 user ID, Figma는 ID/handle, Google은 personName·permissionId와 제공 가능한 표시 정보를 사용합니다.
                    provider가 활동 응답에 이메일을 제공하지 않을 수 있으므로 이메일만으로 계정을 식별하지 않습니다.
                    actorKey는 원본 계정 식별값을 노출하지 않는 불투명 키이며 저장 요청에 그대로 전달합니다.
                    다른 멤버의 원본 provider ID는 제공하지 않고 이메일 형식의 값은 마스킹합니다.
                    현재 로그인 멤버 ID는 currentProjectMemberId로 제공하며, 각 멤버는 자신의 매핑만 저장·변경·해제할 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "팀원 매핑 및 선택 가능한 provider 계정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 또는 provider 연동 정보 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationActorMappingListResponse>> getActorMappings(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "github") String provider,
            Long userId
    );

    @Operation(
            tags = "Integration",
            summary = "5-2. 내 provider 계정 매핑 저장·변경",
            description = """
                    계정 매핑 조회의 availableProviderActors에서 본인 계정의 actorKey를 선택해 현재 프로젝트 멤버에게 연결합니다.
                    projectMemberId는 요청으로 받지 않고 JWT 사용자와 ACTIVE 프로젝트 멤버십으로 서버가 결정합니다.
                    같은 provider에서 기존 내 매핑이 있으면 선택한 provider 계정으로 교체합니다.
                    이미 다른 프로젝트 멤버가 선택한 provider 계정 또는 같은 이메일·로그인 별칭은 중복 연결할 수 없습니다.
                    저장 즉시 기존 수집 활동의 projectMemberId에도 반영됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "현재 멤버 actor 매핑 저장 또는 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "지원하지 않는 provider 또는 actorKey 누락",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트, provider 연동 정보, 또는 선택한 provider 계정 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "다른 프로젝트 멤버의 provider 계정 또는 별칭과 충돌",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationActorMappingResponse>> saveMyActorMapping(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "github") String provider,
            IntegrationActorMappingRequest request,
            Long userId
    );

    @Operation(
            tags = "Integration",
            summary = "5-3. 내 provider 계정 매핑 해제",
            description = """
                    현재 프로젝트 멤버가 직접 저장한 provider 계정 매핑과 별칭을 삭제합니다.
                    기존에 해당 provider 계정으로 귀속된 수집 활동의 projectMemberId도 즉시 비웁니다.
                    provider 계정 OAuth 연결이나 프로젝트 수집 대상 리소스는 삭제하지 않습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "현재 멤버 actor 매핑 해제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트, provider 연동 정보, 또는 현재 멤버 매핑 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationActorMappingResponse>> removeMyActorMapping(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "github") String provider,
            Long userId
    );

    @Operation(tags = "Integration",
            summary = "3-2. Notion 등록 후보 조회",
            description = """
                    Notion OAuth 승인으로 접근 가능한 page와 data source 후보를 조회합니다.
                    응답의 providerResourceId와 resourceType을 Notion 수집 대상 등록 요청에 그대로 사용합니다.
                    query를 넣으면 Notion 제목 기준 검색어로 전달합니다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Notion 후보 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아니거나 Notion 리소스 접근 권한 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 또는 Notion 연동 정보 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "Notion provider 일시 장애",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<List<IntegrationResourceCandidateResponse>>> getNotionResourceCandidates(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "Notion 검색어. 생략하면 접근 가능한 후보를 넓게 조회합니다.", example = "회의록") String query,
            Long userId
    );

    @Operation(tags = "Integration",
            summary = "3-3. Notion 수집 대상 등록",
            description = "후보 조회에서 사용자가 선택한 page 또는 data source ID와 종류를 등록합니다. 서버가 Notion API로 해당 ID의 접근 권한을 재검증합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Notion 리소스 등록 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "INTEGRATION005",
                                      "message": "외부 연동 리소스를 등록했습니다.",
                                      "result": {
                                        "resourceId": 10,
                                        "providerResourceId": "1a2b3c4d-5e6f-7890-abcd-ef1234567890",
                                        "resourceType": "NOTION_PAGE",
                                        "resourceName": "Plog 회의록",
                                        "resourceUrl": "https://www.notion.so/...",
                                        "resourceStatus": "ACTIVE",
                                        "lastModifiedAt": "2026-07-26T08:20:00Z",
                                        "lastCollectedAt": null
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아니거나 Notion 리소스 접근 권한 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트, Notion 연동 정보, 또는 외부 리소스 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 등록된 외부 리소스",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "Notion provider 일시 장애",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationResourceResponse>> registerNotionResource(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            NotionResourceRegisterRequest request,
            Long userId
    );

    @Operation(tags = "Integration",
            summary = "3-4. Google Docs·Slides 수집 대상 등록",
            description = "Google Picker가 선택한 fileId만 받습니다. name, mimeType, URL은 신뢰하지 않고 서버가 Drive API로 재조회해 Docs 또는 Slides인지 판별합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Google 리소스 등록 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "INTEGRATION005",
                                      "message": "외부 연동 리소스를 등록했습니다.",
                                      "result": {
                                        "resourceId": 11,
                                        "providerResourceId": "1a2b3c4d5e6f",
                                        "resourceType": "GOOGLE_PRESENTATION",
                                        "resourceName": "Plog 발표자료",
                                        "resourceUrl": "https://docs.google.com/presentation/d/1a2b3c4d5e6f/edit",
                                        "resourceStatus": "ACTIVE",
                                        "lastModifiedAt": "2026-07-26T08:20:00Z",
                                        "lastCollectedAt": null
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Google Docs 또는 네이티브 Google Slides가 아닌 파일",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아니거나 Google 리소스 접근 권한 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트, Google 연동 정보, 또는 외부 리소스 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 등록된 외부 리소스",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "Google provider 일시 장애",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationResourceResponse>> registerGoogleResource(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            GoogleResourceRegisterRequest request,
            Long userId
    );

    @Operation(tags = "Integration",
            summary = "3-5. Figma Design File 수집 대상 등록",
            description = "사용자가 입력한 Figma Design File URL만 받습니다. 서버가 file key를 추출하고 Figma API 접근 권한을 재검증합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Figma 리소스 등록 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "INTEGRATION005",
                                      "message": "외부 연동 리소스를 등록했습니다.",
                                      "result": {
                                        "resourceId": 12,
                                        "providerResourceId": "abc123",
                                        "resourceType": "FIGMA_FILE",
                                        "resourceName": "Plog 화면설계서",
                                        "resourceUrl": "https://www.figma.com/design/abc123/Plog",
                                        "resourceStatus": "ACTIVE",
                                        "lastModifiedAt": "2026-07-26T08:20:00Z",
                                        "lastCollectedAt": null
                                      }
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Figma URL 형식이 올바르지 않음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아니거나 Figma 리소스 접근 권한 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트, Figma 연동 정보, 또는 외부 리소스 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "이미 등록된 외부 리소스",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "Figma provider 일시 장애",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationResourceResponse>> registerFigmaResource(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            FigmaResourceRegisterRequest request,
            Long userId
    );

    @Operation(
            tags = "Integration",
            summary = "4. 외부 연동 데이터 수동 수집",
            description = """
                    프로젝트에 ACTIVE 상태로 등록된 GitHub, Notion, Google, Figma 리소스의 활동 원문을 수집합니다.
                    프로젝트가 완료 상태로 전환될 때 내부 로직으로 자동 수집되며, 이 API는 수집 장애 복구나 재동기화가 필요할 때 사용할 수 있습니다.
                    프로젝트가 진행 중이어도 ACTIVE 멤버가 실행할 수 있으며 프로젝트 상태는 변경하지 않습니다.
                    일부 리소스 수집에 실패해도 가능한 리소스는 계속 수집하고 failures에 resourceId, linkType, resourceName과 원인을 반환합니다.
                    requestedResourceCount는 시도한 리소스 수, collectedResourceCount는 성공한 리소스 수입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "수집 실행 완료. 부분 실패 정보는 failures에 포함"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ACTIVE 프로젝트 멤버가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "프로젝트 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503",
                    description = "GitHub 리소스 동기화 또는 provider 일시 장애",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<IntegrationCollectionResponse>> collectIntegrationData(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            Long userId
    );

    @Operation(hidden = true)
    RedirectView integrationCallback(
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "notion") String provider,
            @Parameter(description = "Plog가 발급한 일회용 OAuth state") String state,
            @Parameter(description = "GitHub App 설치 후 GitHub가 전달하는 installation_id") String installationId,
            @Parameter(description = "OAuth provider가 전달하는 authorization code") String code
    );
}
