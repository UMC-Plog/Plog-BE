package com.plog.domain.integration.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.integration.config.IntegrationRedirectProperties;
import com.plog.domain.integration.dto.NotionResourceType;
import com.plog.domain.integration.dto.request.FigmaResourceRegisterRequest;
import com.plog.domain.integration.dto.request.GoogleResourceRegisterRequest;
import com.plog.domain.integration.dto.request.NotionResourceRegisterRequest;
import com.plog.domain.integration.dto.response.IntegrationCollectionFailureResponse;
import com.plog.domain.integration.dto.response.IntegrationCollectionResponse;
import com.plog.domain.integration.dto.response.IntegrationActorMappingListResponse;
import com.plog.domain.integration.dto.response.IntegrationActorMappingResponse;
import com.plog.domain.integration.dto.response.IntegrationProviderActorResponse;
import com.plog.domain.integration.dto.response.IntegrationItemResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceCandidateResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceListResponse;
import com.plog.domain.integration.dto.response.IntegrationResourceResponse;
import com.plog.domain.integration.dto.response.IntegrationStatusResponse;
import com.plog.domain.integration.entity.IntegrationResourceStatus;
import com.plog.domain.integration.entity.IntegrationResourceType;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.service.FigmaIntegrationService;
import com.plog.domain.integration.service.GithubIntegrationService;
import com.plog.domain.integration.service.GoogleIntegrationService;
import com.plog.domain.integration.service.GooglePickerAccessTokenService;
import com.plog.domain.integration.service.IntegrationDataCollectionService;
import com.plog.domain.integration.service.IntegrationActorMappingManagementService;
import com.plog.domain.integration.service.IntegrationResourceService;
import com.plog.domain.integration.service.IntegrationService;
import com.plog.domain.integration.service.NotionIntegrationService;
import com.plog.global.api.error.IntegrationErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IntegrationController.class)
@AutoConfigureMockMvc(addFilters = false)
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IntegrationService integrationService;

    @MockitoBean
    private IntegrationActorMappingManagementService integrationActorMappingManagementService;

    @MockitoBean
    private IntegrationResourceService integrationResourceService;

    @MockitoBean
    private IntegrationDataCollectionService integrationDataCollectionService;

    @MockitoBean
    private GithubIntegrationService githubIntegrationService;

    @MockitoBean
    private FigmaIntegrationService figmaIntegrationService;

    @MockitoBean
    private NotionIntegrationService notionIntegrationService;

    @MockitoBean
    private GoogleIntegrationService googleIntegrationService;

    @MockitoBean
    private GooglePickerAccessTokenService googlePickerAccessTokenService;

    @MockitoBean
    private IntegrationRedirectProperties integrationRedirectProperties;

    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 외부 툴 연동 상태를 공통 ApiResponse 형식으로 조회하고 accessToken은 노출하지 않는다")
    void getProjectIntegrationsReturnsApiResponse() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationService.getProjectIntegrations(eq(projectId), eq(userId)))
                .willReturn(new IntegrationStatusResponse(projectId, 100L, List.of(
                        new IntegrationItemResponse(LinkType.GITHUB, true, "github-user"),
                        new IntegrationItemResponse(LinkType.FIGMA, false, null),
                        new IntegrationItemResponse(LinkType.NOTION, true, "notion-user"),
                        new IntegrationItemResponse(LinkType.GOOGLE_DOCS, false, null),
                        new IntegrationItemResponse(LinkType.GOOGLE_SLIDES, false, null)
                )));

        mockMvc.perform(get("/api/projects/{projectId}/integrations", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT001"))
                .andExpect(jsonPath("$.message").value("프로젝트 연동 상태를 조회했습니다."))
                .andExpect(jsonPath("$.result.projectId").value(projectId))
                .andExpect(jsonPath("$.result.projectMemberId").value(100L))
                .andExpect(jsonPath("$.result.integrations[0].linkType").value("GITHUB"))
                .andExpect(jsonPath("$.result.integrations[0].linked").value(true))
                .andExpect(jsonPath("$.result.integrations[0].connectedAccountName").value("github-user"))
                .andExpect(jsonPath("$.result.integrations[1].linkType").value("FIGMA"))
                .andExpect(jsonPath("$.result.integrations[1].linked").value(false))
                .andExpect(jsonPath("$.result.integrations[1].connectedAccountName").value(nullValue()))
                .andExpect(jsonPath("$.result.integrations[2].linkType").value("NOTION"))
                .andExpect(jsonPath("$.result.integrations[2].linked").value(true))
                .andExpect(jsonPath("$.result.integrations[2].connectedAccountName").value("notion-user"))
                .andExpect(jsonPath("$.result.integrations[3].linkType").value("GOOGLE_DOCS"))
                .andExpect(jsonPath("$.result.integrations[3].linked").value(false))
                .andExpect(jsonPath("$.result.integrations[3].connectedAccountName").value(nullValue()))
                .andExpect(jsonPath("$.result.integrations[4].linkType").value("GOOGLE_SLIDES"))
                .andExpect(jsonPath("$.result.integrations[4].linked").value(false))
                .andExpect(jsonPath("$.result.integrations[4].connectedAccountName").value(nullValue()))
                .andExpect(jsonPath("$.result.integrations[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.integrations[1].accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.integrations[2].accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.integrations[3].accessToken").doesNotExist())
                .andExpect(jsonPath("$.result.integrations[4].accessToken").doesNotExist());
    }

    @Test
    @DisplayName("프로젝트가 없으면 404와 PROJECT001을 반환한다")
    void getProjectIntegrationsReturnsNotFound() throws Exception {
        Long projectId = 404L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationService.getProjectIntegrations(eq(projectId), eq(userId)))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(get("/api/projects/{projectId}/integrations", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT001"))
                .andExpect(jsonPath("$.message").value("프로젝트를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("활성 프로젝트 멤버가 아니면 403과 PROJECT002를 반환한다")
    void getProjectIntegrationsReturnsForbidden() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationService.getProjectIntegrations(eq(projectId), eq(userId)))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        mockMvc.perform(get("/api/projects/{projectId}/integrations", projectId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT002"))
                .andExpect(jsonPath("$.message").value("활성 프로젝트 멤버만 접근할 수 있습니다."));
    }

    @Test
    @DisplayName("projectId 타입이 올바르지 않으면 400과 COMMON400을 반환한다")
    void getProjectIntegrationsRejectsMalformedProjectId() throws Exception {
        authenticate(10L);

        mockMvc.perform(get("/api/projects/{projectId}/integrations", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

        verifyNoInteractions(integrationService);
    }

    @Test
    @DisplayName("provider별 등록된 외부 리소스를 조회한다")
    void getResourcesReturnsRegisteredResources() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.getResources(eq(projectId), eq(userId), eq(LinkType.FIGMA)))
                .willReturn(new IntegrationResourceListResponse(projectId, LinkType.FIGMA, List.of(
                        new IntegrationResourceResponse(
                                1L,
                                "figma-file-key",
                                IntegrationResourceType.FIGMA_FILE,
                                "Plog Design",
                                "https://www.figma.com/design/figma-file-key/Plog",
                                IntegrationResourceStatus.ACTIVE,
                                Instant.parse("2026-07-26T08:20:00Z"),
                                null
                        )
                )));

        mockMvc.perform(get("/api/projects/{projectId}/integrations/{provider}/resources", projectId, "figma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTEGRATION004"))
                .andExpect(jsonPath("$.result.projectId").value(projectId))
                .andExpect(jsonPath("$.result.linkType").value("FIGMA"))
                .andExpect(jsonPath("$.result.resources[0].resourceType").value("FIGMA_FILE"))
                .andExpect(jsonPath("$.result.resources[0].resourceName").value("Plog Design"))
                .andExpect(jsonPath("$.result.resources[0].lastModifiedAt").value("2026-07-26T08:20:00Z"));
    }

    @Test
    @DisplayName("provider에서 발견된 미매핑 계정과 프로젝트 멤버 매핑을 조회한다")
    void getActorMappingsReturnsMappingsAndAvailableProviderActors() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationActorMappingManagementService.getMappings(
                projectId, userId, LinkType.GITHUB
        )).willReturn(new IntegrationActorMappingListResponse(
                projectId,
                LinkType.GITHUB,
                100L,
                List.of(new IntegrationActorMappingResponse(
                        20L, 101L, "김팀원", "팀원", null,
                        "actor:mapped-999", null, "teammate", null
                )),
                List.of(new IntegrationProviderActorResponse(
                        "actor:available-123", null, "wantkdd", "v***@example.com",
                        "wantkdd",
                        4L,
                        Instant.parse("2026-07-01T00:00:00Z"),
                        Instant.parse("2026-07-20T00:00:00Z"),
                        false, null, false
                ))
        ));

        mockMvc.perform(get(
                        "/api/projects/{projectId}/integrations/{provider}/actor-mappings",
                        projectId,
                        "github"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTEGRATION019"))
                .andExpect(jsonPath("$.result.currentProjectMemberId").value(100L))
                .andExpect(jsonPath("$.result.mappings[0].projectMemberId").value(101L))
                .andExpect(jsonPath("$.result.availableProviderActors[0].actorKey").value("actor:available-123"))
                .andExpect(jsonPath("$.result.availableProviderActors[0].providerActorId").isEmpty())
                .andExpect(jsonPath("$.result.availableProviderActors[0].displayName").value("wantkdd"))
                .andExpect(jsonPath("$.result.availableProviderActors[0].activityCount").value(4L))
                .andExpect(jsonPath("$.result.availableProviderActors[0].mapped").value(false));
    }

    @Test
    @DisplayName("선택한 provider 계정을 현재 프로젝트 멤버에게 저장한다")
    void saveMyActorMappingReturnsSavedMapping() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationActorMappingManagementService.saveMyMapping(
                eq(projectId), eq(userId), eq(LinkType.GITHUB), any()
        )).willReturn(new IntegrationActorMappingResponse(
                20L, 100L, "유상완", "바나", null,
                "actor:available-123", "123", "wantkdd", "v***@example.com"
        ));

        mockMvc.perform(put(
                        "/api/projects/{projectId}/integrations/{provider}/actor-mappings/me",
                        projectId,
                        "github"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorKey": "actor:available-123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTEGRATION020"))
                .andExpect(jsonPath("$.result.projectMemberId").value(100L))
                .andExpect(jsonPath("$.result.actorKey").value("actor:available-123"));
    }

    @Test
    @DisplayName("현재 프로젝트 멤버의 actor 매핑을 해제한다")
    void removeMyActorMappingReturnsRemovedMapping() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationActorMappingManagementService.removeMyMapping(
                projectId, userId, LinkType.GITHUB
        )).willReturn(new IntegrationActorMappingResponse(
                20L, 100L, "유상완", "바나", null,
                "actor:available-123", "123", "wantkdd", "v***@example.com"
        ));

        mockMvc.perform(delete(
                        "/api/projects/{projectId}/integrations/{provider}/actor-mappings/me",
                        projectId,
                        "github"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTEGRATION021"))
                .andExpect(jsonPath("$.result.mappingId").value(20L));
    }

    @Test
    @DisplayName("다른 멤버가 선택한 actor는 409와 INTEGRATION016을 반환한다")
    void saveMyActorMappingReturnsConflictForClaimedActor() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationActorMappingManagementService.saveMyMapping(
                eq(projectId), eq(userId), eq(LinkType.GITHUB), any()
        )).willThrow(new ApiException(IntegrationErrorCode.ACTOR_ALREADY_MAPPED));

        mockMvc.perform(put(
                        "/api/projects/{projectId}/integrations/{provider}/actor-mappings/me",
                        projectId,
                        "github"
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorKey": "actor:available-123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTEGRATION016"));
    }

    @Test
    @DisplayName("내 actor 매핑이 없으면 해제 요청에 404와 INTEGRATION017을 반환한다")
    void removeMyActorMappingReturnsNotFound() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationActorMappingManagementService.removeMyMapping(
                projectId, userId, LinkType.GITHUB
        )).willThrow(new ApiException(IntegrationErrorCode.ACTOR_MAPPING_NOT_FOUND));

        mockMvc.perform(delete(
                        "/api/projects/{projectId}/integrations/{provider}/actor-mappings/me",
                        projectId,
                        "github"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTEGRATION017"));
    }

    @Test
    @DisplayName("Notion 후보 조회는 접근 가능한 page와 data source 후보를 반환한다")
    void getNotionResourceCandidatesReturnsCandidates() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.getNotionCandidates(eq(projectId), eq(userId), eq("회의록")))
                .willReturn(List.of(new IntegrationResourceCandidateResponse(
                        "notion-page-id",
                        NotionResourceType.PAGE,
                        "Plog 회의록",
                        "https://www.notion.so/notion-page-id",
                        Instant.parse("2026-07-26T08:20:00Z")
                )));

        mockMvc.perform(get("/api/projects/{projectId}/integrations/notion/resources/candidates", projectId)
                        .param("query", "회의록"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTEGRATION004"))
                .andExpect(jsonPath("$.result[0].providerResourceId").value("notion-page-id"))
                .andExpect(jsonPath("$.result[0].resourceType").value("PAGE"))
                .andExpect(jsonPath("$.result[0].resourceName").value("Plog 회의록"))
                .andExpect(jsonPath("$.result[0].lastModifiedAt").value("2026-07-26T08:20:00Z"));
    }

    @Test
    @DisplayName("Notion 수집 대상을 등록한다")
    void registerNotionResourceReturnsCreatedResource() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.registerNotion(
                eq(projectId), eq(userId), any(NotionResourceRegisterRequest.class)))
                .willReturn(new IntegrationResourceResponse(
                        1L,
                        "notion-page-id",
                        IntegrationResourceType.NOTION_PAGE,
                        "Plog 회의록",
                        "https://www.notion.so/notion-page-id",
                        IntegrationResourceStatus.ACTIVE,
                        Instant.parse("2026-07-26T08:20:00Z"),
                        null
                ));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/notion/resources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceType": "PAGE",
                                  "providerResourceId": "notion-page-id"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INTEGRATION005"))
                .andExpect(jsonPath("$.result.resourceType").value("NOTION_PAGE"))
                .andExpect(jsonPath("$.result.resourceStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("Google Picker가 선택한 fileId로 Docs 또는 Slides 수집 대상을 등록한다")
    void registerGoogleResourceReturnsCreatedResource() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.registerGoogle(
                eq(projectId), eq(userId), eq(LinkType.GOOGLE_DOCS), any(GoogleResourceRegisterRequest.class)))
                .willReturn(new IntegrationResourceResponse(
                        2L,
                        "google-file-id",
                        IntegrationResourceType.GOOGLE_DOCUMENT,
                        "Plog 기획서",
                        "https://docs.google.com/document/d/google-file-id",
                        IntegrationResourceStatus.ACTIVE,
                        Instant.parse("2026-07-26T08:20:00Z"),
                        null
                ));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/google-docs/resources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "fileId": "google-file-id"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INTEGRATION005"))
                .andExpect(jsonPath("$.result.resourceType").value("GOOGLE_DOCUMENT"))
                .andExpect(jsonPath("$.result.resourceName").value("Plog 기획서"));
    }

    @Test
    @DisplayName("Figma 파일 URL로 Design File 수집 대상을 등록한다")
    void registerFigmaResourceReturnsCreatedResource() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.registerFigma(
                eq(projectId), eq(userId), any(FigmaResourceRegisterRequest.class)))
                .willReturn(new IntegrationResourceResponse(
                        3L,
                        "figma-file-key",
                        IntegrationResourceType.FIGMA_FILE,
                        "Plog Design",
                        "https://www.figma.com/design/figma-file-key/Plog",
                        IntegrationResourceStatus.ACTIVE,
                        Instant.parse("2026-07-26T08:20:00Z"),
                        null
                ));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/figma/resources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileUrl": "https://www.figma.com/design/figma-file-key/Plog"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INTEGRATION005"))
                .andExpect(jsonPath("$.result.resourceType").value("FIGMA_FILE"))
                .andExpect(jsonPath("$.result.resourceName").value("Plog Design"));
    }

    @Test
    @DisplayName("등록 리소스 조회에서 provider 연동이 없으면 404와 INTEGRATION006을 반환한다")
    void getResourcesReturnsIntegrationNotFound() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.getResources(eq(projectId), eq(userId), eq(LinkType.FIGMA)))
                .willThrow(new ApiException(IntegrationErrorCode.PROJECT_INTEGRATION_NOT_FOUND));

        mockMvc.perform(get("/api/projects/{projectId}/integrations/{provider}/resources", projectId, "figma"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTEGRATION006"));
    }

    @Test
    @DisplayName("Notion 후보 조회에서 provider 접근 권한이 없으면 403과 INTEGRATION010을 반환한다")
    void getNotionResourceCandidatesReturnsAccessDenied() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.getNotionCandidates(eq(projectId), eq(userId), eq("회의록")))
                .willThrow(new ApiException(IntegrationErrorCode.PROVIDER_RESOURCE_ACCESS_DENIED));

        mockMvc.perform(get("/api/projects/{projectId}/integrations/notion/resources/candidates", projectId)
                        .param("query", "회의록"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTEGRATION010"));
    }

    @Test
    @DisplayName("Notion 등록 대상이 이미 있으면 409와 INTEGRATION008을 반환한다")
    void registerNotionResourceReturnsAlreadyRegistered() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.registerNotion(
                eq(projectId), eq(userId), any(NotionResourceRegisterRequest.class)))
                .willThrow(new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_ALREADY_REGISTERED));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/notion/resources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceType": "PAGE",
                                  "providerResourceId": "notion-page-id"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTEGRATION008"));
    }

    @Test
    @DisplayName("Google 등록 대상이 provider에서 없으면 404와 INTEGRATION007을 반환한다")
    void registerGoogleResourceReturnsResourceNotFound() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.registerGoogle(
                eq(projectId), eq(userId), eq(LinkType.GOOGLE_DOCS), any(GoogleResourceRegisterRequest.class)))
                .willThrow(new ApiException(IntegrationErrorCode.EXTERNAL_RESOURCE_NOT_FOUND));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/google-docs/resources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "fileId": "missing-google-file-id"
                    }
                    """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTEGRATION007"));
    }

    @Test
    @DisplayName("Figma 리소스 접근 권한이 없으면 403과 INTEGRATION010을 반환한다")
    void registerFigmaResourceReturnsAccessDenied() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationResourceService.registerFigma(
                eq(projectId), eq(userId), any(FigmaResourceRegisterRequest.class)))
                .willThrow(new ApiException(IntegrationErrorCode.PROVIDER_RESOURCE_ACCESS_DENIED));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/figma/resources", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fileUrl": "https://www.figma.com/design/figma-file-key/Plog"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTEGRATION010"));
    }

    @Test
    @DisplayName("외부 연동 데이터 수동 수집 결과에 성공 수와 실패 리소스 정보를 반환한다")
    void collectIntegrationDataReturnsCollectionResult() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationDataCollectionService.collectNow(eq(projectId), eq(userId)))
                .willReturn(new IntegrationCollectionResponse(
                        projectId,
                        3,
                        2,
                        List.of(new IntegrationCollectionFailureResponse(
                                12L,
                                LinkType.GOOGLE_DOCS,
                                "캡스톤 발표자료",
                                "provider resource access denied"
                        ))
                ));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/collect", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("INTEGRATION006"))
                .andExpect(jsonPath("$.result.projectId").value(projectId))
                .andExpect(jsonPath("$.result.requestedResourceCount").value(3))
                .andExpect(jsonPath("$.result.collectedResourceCount").value(2))
                .andExpect(jsonPath("$.result.failures[0].resourceId").value(12L))
                .andExpect(jsonPath("$.result.failures[0].linkType").value("GOOGLE_DOCS"))
                .andExpect(jsonPath("$.result.failures[0].resourceName").value("캡스톤 발표자료"))
                .andExpect(jsonPath("$.result.failures[0].reason").value("provider resource access denied"));
    }

    @Test
    @DisplayName("수집할 프로젝트가 없으면 404와 PROJECT001을 반환한다")
    void collectIntegrationDataReturnsProjectNotFound() throws Exception {
        Long projectId = 999L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationDataCollectionService.collectNow(projectId, userId))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/collect", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT001"));
    }

    @Test
    @DisplayName("활성 프로젝트 멤버가 아니면 수집 요청에 403과 PROJECT002를 반환한다")
    void collectIntegrationDataReturnsProjectMemberRequired() throws Exception {
        Long projectId = 1L;
        Long userId = 10L;
        authenticate(userId);
        given(integrationDataCollectionService.collectNow(projectId, userId))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        mockMvc.perform(post("/api/projects/{projectId}/integrations/collect", projectId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT002"));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null)
        );
    }
}
