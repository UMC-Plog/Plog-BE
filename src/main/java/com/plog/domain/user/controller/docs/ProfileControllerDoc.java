package com.plog.domain.user.controller.docs;

import com.plog.domain.user.dto.request.ProfileUpdateRequest;
import com.plog.domain.user.dto.response.ProfileResponse;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;

@Tag(name = "User", description = "사용자 계정 및 프로필 관리 API")
public interface ProfileControllerDoc {

    @Operation(
            summary = "내 프로필 조회",
            description = """
                    마이페이지 및 프로필 수정 화면의 초기값입니다.
                    - nameChangeAvailable이 false면 실명 [변경] 버튼을 비활성화해야 합니다.
                    - profilePreset이 null이면 기본(회색) 아바타를 렌더합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공 (PROFILE002)"),
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
                    responseCode = "403", description = "탈퇴 처리 중인 계정 (AUTH016)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH016",
                                      "message": "탈퇴 처리 중인 계정입니다."
                                    }
                                    """)))
    })
    ResponseEntity<ApiResponse<ProfileResponse>> getProfile(Long userId);

    @Operation(
            summary = "닉네임 중복확인 (마이페이지)",
            description = """
                    프로필 수정 화면의 [중복 확인] 버튼용입니다. 닉네임은 서버 전역에서 유일해야 합니다.
                    - 본인의 현재 닉네임을 그대로 보내면 사용 가능(PROFILE003)으로 통과합니다.
                    - 회원가입용은 GET /api/auth/nickname/check 이며 성공 코드가 AUTH003으로 다릅니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "사용 가능 (PROFILE003)"),
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
                    responseCode = "403", description = "탈퇴 처리 중인 계정 (AUTH016)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH016",
                                      "message": "탈퇴 처리 중인 계정입니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 사용 중인 닉네임 (AUTH003)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH003",
                                      "message": "이미 사용 중인 닉네임입니다."
                                    }
                                    """)))
    })
    // 제약은 여기(인터페이스)에만 선언한다. @Validated(클래스)가 붙은 구현체(ProfileController)의
    // 오버라이딩 메서드에 파라미터 제약을 추가로 붙이면 "강화" 취급되어 ConstraintDeclarationException
    // (HV000151)이 난다 — 그래서 구현체 쪽 시그니처는 애노테이션 없이 그대로 둔다.
    // 그래도 검증 자체는 살아있다: Bean Validation의 실행형(method) 검증은 대상 빈의 타입 계층 전체
    // (인터페이스 포함)에서 제약 메타데이터를 모아 적용하므로, AOP 프록시를 통해 들어오는 실제 호출에서
    // 이 인터페이스 선언만으로도 그대로 강제된다 — ProfileControllerTest의 빈 닉네임 요청 테스트로 확인함.
    ResponseEntity<ApiResponse<Void>> checkNickname(Long userId, @NotBlank String nickname);

    @Operation(
            summary = "프로필 수정",
            description = """
                    실명 / 닉네임 / 아바타 프리셋을 한 번에 수정합니다. **보낸 필드만 반영**됩니다.
                    - 실명은 계정당 1회만 변경 가능합니다(USER001). 현재 값과 같은 값을 보내면 권리를 소모하지 않습니다.
                    - 세 필드 모두 생략하거나 모두 현재 값과 같으면 아무것도 바꾸지 않고 200을 반환합니다.
                    - 하나라도 실패하면 전체가 롤백되어 일부만 반영되는 일이 없습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "변경 성공 (PROFILE001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "잘못된 프리셋 값 등 요청 본문을 읽을 수 없음 (COMMON400_1)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON400_1",
                                      "message": "요청을 읽을 수 없습니다."
                                    }
                                    """))),
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
                    responseCode = "403", description = "탈퇴 처리 중인 계정 (AUTH016)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH016",
                                      "message": "탈퇴 처리 중인 계정입니다."
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "닉네임 중복(AUTH003) 또는 실명 변경 횟수 초과(USER001)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "닉네임 중복", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH003",
                                              "message": "이미 사용 중인 닉네임입니다."
                                            }
                                            """),
                                    @ExampleObject(name = "실명 변경 초과", value = """
                                            {
                                              "isSuccess": false,
                                              "code": "USER001",
                                              "message": "실명은 1회만 변경할 수 있습니다."
                                            }
                                            """)
                            }))
    })
    // updateProfile도 checkNickname과 같은 이유로 제약을 인터페이스에만 선언한다(HV000151 회피).
    // ProfileUpdateRequest의 name/nickname 공백 방지(@Pattern)까지 이 @Valid 하나로 함께 캐스케이드된다.
    ResponseEntity<ApiResponse<Void>> updateProfile(Long userId, @Valid ProfileUpdateRequest request);
}
