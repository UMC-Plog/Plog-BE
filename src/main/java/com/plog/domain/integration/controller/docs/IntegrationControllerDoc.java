package com.plog.domain.integration.controller.docs;

import com.plog.domain.integration.dto.request.FigmaResourceRegisterRequest;
import com.plog.domain.integration.dto.request.GoogleResourceRegisterRequest;
import com.plog.domain.integration.dto.request.NotionResourceRegisterRequest;
import com.plog.domain.integration.dto.response.IntegrationAuthorizationResponse;
import com.plog.domain.integration.dto.response.IntegrationDisconnectionResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceCandidateResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceListResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceResponse;
import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.view.RedirectView;

@Tag(name = "Integration", description = "프로젝트 외부 계정 연동 API")
public interface IntegrationControllerDoc {

    @Operation(
            summary = "프로젝트 외부 연동 상태 조회",
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
            summary = "외부 계정 연동 URL 발급",
            description = """
                    프로젝트 ACTIVE 멤버가 provider 승인 화면으로 이동할 URL을 발급합니다.
                    provider는 github, figma, notion, google 중 하나입니다.
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
            summary = "외부 계정 연동 해제",
            description = """
                    프로젝트에 저장된 provider 연동 정보를 삭제합니다.
                    현재 구현은 Plog DB의 연결 정보를 즉시 삭제합니다. provider 콘솔/GitHub App 설치 자체의 철회 검증은 후속 이슈 범위입니다.
                    provider는 github, figma, notion, google 중 하나입니다.
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

    @Operation(summary = "등록된 외부 연동 리소스 조회",
            description = """
                    프로젝트 ACTIVE 멤버가 provider에 등록된 수집 대상 리소스를 조회합니다.
                    provider는 github, figma, notion, google 중 하나입니다.
                    GitHub repository는 GitHub App 설치 callback에서 자동 등록되며, 나머지 provider는 별도 등록 API를 호출해야 합니다.
                    """)
    ResponseEntity<ApiResponse<IntegrationResourceListResponse>> getResources(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "figma") String provider,
            Long userId
    );

    @Operation(summary = "Notion 등록 후보 조회",
            description = """
                    Notion OAuth 승인으로 접근 가능한 page와 data source 후보를 조회합니다.
                    응답의 providerResourceId와 resourceType을 Notion 수집 대상 등록 요청에 그대로 사용합니다.
                    query를 넣으면 Notion 제목 기준 검색어로 전달합니다.
                    """)
    ResponseEntity<ApiResponse<List<IntegrationResourceCandidateResponse>>> getNotionResourceCandidates(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            @Parameter(description = "Notion 검색어. 생략하면 접근 가능한 후보를 넓게 조회합니다.", example = "회의록") String query,
            Long userId
    );

    @Operation(summary = "Notion 수집 대상 등록",
            description = "후보 조회에서 사용자가 선택한 page 또는 data source ID와 종류를 등록합니다. 서버가 Notion API로 해당 ID의 접근 권한을 재검증합니다.")
    ResponseEntity<ApiResponse<IntegrationResourceResponse>> registerNotionResource(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            NotionResourceRegisterRequest request,
            Long userId
    );

    @Operation(summary = "Google Docs·Slides 수집 대상 등록",
            description = "Google Picker가 선택한 fileId만 받습니다. name, mimeType, URL은 신뢰하지 않고 서버가 Drive API로 재조회해 Docs 또는 Slides인지 판별합니다.")
    ResponseEntity<ApiResponse<IntegrationResourceResponse>> registerGoogleResource(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            GoogleResourceRegisterRequest request,
            Long userId
    );

    @Operation(summary = "Figma Design File 수집 대상 등록",
            description = "사용자가 입력한 Figma Design File URL만 받습니다. 서버가 file key를 추출하고 Figma API 접근 권한을 재검증합니다.")
    ResponseEntity<ApiResponse<IntegrationResourceResponse>> registerFigmaResource(
            @Parameter(description = "프로젝트 ID", example = "1") Long projectId,
            FigmaResourceRegisterRequest request,
            Long userId
    );

    @Operation(
            summary = "외부 계정 연동 callback",
            description = """
                    provider가 승인 완료 후 호출하는 callback입니다.
                    프론트가 직접 호출하는 API가 아니며, 성공/실패 결과는 설정된 프론트 redirect URL의 query string으로 전달됩니다.
                    GitHub는 installation_id, OAuth provider는 code를 전달합니다.
                    """
    )
    RedirectView integrationCallback(
            @Parameter(description = "provider 식별자: github, figma, notion, google", example = "notion") String provider,
            @Parameter(description = "Plog가 발급한 일회용 OAuth state") String state,
            @Parameter(description = "GitHub App 설치 후 GitHub가 전달하는 installation_id") String installationId,
            @Parameter(description = "OAuth provider가 전달하는 authorization code") String code
    );
}
