package com.plog.domain.user.controller;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.plog.domain.user.dto.request.ProfileUpdateRequest;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.service.ProfileService;
import com.plog.global.security.jwt.JwtProvider;
import com.plog.global.security.jwt.MediaTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

// PATCH /api/profile/preset(ProfilePresetUpdateRequest)은 Task 8에서 PATCH /api/profile
// (ProfileUpdateRequest)로 통합되었다. preset이 더 이상 필수가 아니므로,
// "preset 누락 시 400" 검증은 의미가 없어져 "빈 바디는 no-op으로 통과"로 대체했다.
@WebMvcTest(ProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProfileService profileService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private MediaTokenProvider mediaTokenProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(7L, null));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updatesProfileForTheAuthenticatedUser() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preset":"OTTER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROFILE001"));

        then(profileService).should().updateProfile(7L, new ProfileUpdateRequest(null, null, ProfilePreset.OTTER));
    }

    @Test
    void acceptsAnEmptyBodyAsANoOp() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROFILE001"));

        then(profileService).should().updateProfile(7L, new ProfileUpdateRequest(null, null, null));
    }

    // Important 2: name/nickname은 선택 필드라 @NotBlank는 못 쓰고 @Pattern(".*\\S.*")으로 공백만 막는다.
    // 이 제약은 ProfileService 단위 테스트로는 검증할 수 없다 — 서비스를 new로 직접 만들어 호출하면
    // Bean Validation을 태우는 Spring MVC의 @RequestBody 처리 자체를 거치지 않기 때문이다.
    // 그래서 이 컨트롤러 테스트가 실제로 이 제약이 살아있는지 확인하는 유일한 지점이다.
    @Test
    void rejectsBlankNameWithoutTouchingTheService() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        then(profileService).shouldHaveNoInteractions();
    }

    // Minor 6: PATCH /api/profile의 문서화된 COMMON400_1(요청을 읽을 수 없음)이 실제로도 그 코드로
    // 나오는지 확인한다. preset이 ProfilePreset enum에 없는 값이면 Jackson이 역직렬화에 실패해
    // HttpMessageNotReadableException이 되고, GlobalExceptionHandler가 COMMON400_1로 매핑한다.
    @Test
    void rejectsUnparseablePresetValue() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preset":"BOGUS"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));

        then(profileService).shouldHaveNoInteractions();
    }

    // Minor 4: @NotBlank를 ProfileControllerDoc 인터페이스에서만 선언하고 구현체(ProfileController)에서는
    // 뺐다(하우스 패턴). 그래도 @Validated 클래스의 AOP 실행형 검증이 인터페이스 제약까지 모아서 적용하므로
    // 빈 닉네임 쿼리 파라미터는 여전히 막혀야 한다 — 이 테스트가 그 증거다.
    @Test
    void rejectsBlankNicknameQueryParamWithoutTouchingTheService() throws Exception {
        mockMvc.perform(get("/api/profile/nickname/check").param("nickname", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        then(profileService).shouldHaveNoInteractions();
    }
}
