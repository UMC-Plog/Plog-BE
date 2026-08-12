package com.plog.domain.report.controller.docs;

import com.plog.domain.report.dto.response.ReportDetailResponse;
import com.plog.domain.report.dto.response.ReportMemberResultResponse;
import com.plog.domain.report.dto.response.ReportPdfDownloadResponse;
import com.plog.domain.report.dto.response.ReportSearchResponse;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;

@Tag(name = "Report", description = "피어 평가, 자기 피드백 및 리포트 생성·조회 API")
public interface ReportControllerDoc {

    @Operation(
            summary = "리포트 목록 조회",
            description = """
                    ACTIVE 멤버십의 프로젝트 리포트를 Slice로 조회합니다.
                    프로젝트와 리포트는 1:1 관계이며, 프론트는 hasNext=true일 때 다음 page를 요청하면 됩니다.
                    정렬은 완료 시각 내림차순, 리포트 ID 내림차순입니다. 완료 시각이 없는 리포트는 뒤에 배치됩니다.
                    ZIP 다운로드 버튼은 reportStatus가 아니라 pdfAvailable로 판단하세요 —
                    발행은 됐지만 ZIP 업로드가 실패해 COMPLETED인데 false인 리포트가 있을 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 목록 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 조건",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<SliceResponse<ReportSearchResponse>>> getReports(
            Long userId,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "리포트 검색",
            description = """
                    ACTIVE 멤버십의 프로젝트 리포트를 프로젝트명과 완료 기간으로 Slice 검색합니다.
                    keyword는 프로젝트명 검색에 사용하고, startDate/endDate는 리포트 완료일 범위 필터입니다.
                    프론트는 hasNext=true일 때 다음 page를 요청하면 됩니다.
                    ZIP 다운로드 버튼은 reportStatus가 아니라 pdfAvailable로 판단하세요 —
                    발행은 됐지만 ZIP 업로드가 실패해 COMPLETED인데 false인 리포트가 있을 수 있습니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 검색 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 검색어, 날짜 범위 또는 페이지 조건",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<SliceResponse<ReportSearchResponse>>> searchReports(
            Long userId,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            @Min(0) int page,
            @Min(1) @Max(100) int size
    );

    @Operation(
            summary = "리포트 상세 조회",
            description = """
                    리포트 1건의 상태와 멤버 요약 목록을 조회합니다. 해당 프로젝트의 ACTIVE 멤버만 접근할 수 있습니다.
                    아직 발행되지 않은 리포트(status=GENERATING/FAILED)도 404가 아니라 200으로 상태를 내려줍니다 —
                    프론트는 status로 "생성 중" 화면을 그리고 폴링하면 됩니다. 이때 members는 항상 빈 배열입니다.
                    멤버별 점수 상세는 members[].projectMemberId로 멤버 결과 API를 호출하세요.
                    pdfAvailable이 true일 때만 팀 PDF와 개인 PDF를 묶은 ZIP 다운로드 URL 발급이 성공합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리포트 상세 조회 성공(발행 전 상태 포함)"
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
                    description = "리포트 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<ReportDetailResponse>> getReport(
            Long userId,
            @Positive Long reportId
    );

    @Operation(
            summary = "리포트 멤버별 결과 조회",
            description = """
                    리포트에 포함된 멤버 1명의 점수 상세를 조회합니다. 해당 프로젝트의 ACTIVE 멤버라면
                    자신뿐 아니라 같은 팀 다른 멤버의 결과도 조회할 수 있습니다(리포트는 팀 공용 산출물입니다).
                    externalToolConnected는 리포트 생성 시점의 프로젝트 ACTIVE 외부 도구 연동 여부입니다.
                    true여도 해당 멤버 계정이 미매핑이거나 점수화 가능한 외부 활동이 없으면 externalScore는 null입니다.
                    이때 나머지 항목의 가중치가 비례 재분배된 finalScore가 내려갑니다.
                    발행 전에는 결과 행이 없어 404입니다 —
                    상태만 필요하면 리포트 상세 API를 사용하세요.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "멤버 결과 조회 성공"
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
                    description = "리포트 없음 또는 해당 멤버의 결과 없음(발행 전 포함)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<ReportMemberResultResponse>> getMemberResult(
            Long userId,
            @Positive Long reportId,
            @Positive Long projectMemberId
    );

    @Operation(
            summary = "리포트 생성 시작",
            description = """
                    리포트 1건의 AI 텍스트를 생성하고 발행합니다. 팀 인사이트와 팀원 전원의 개인 리포트가
                    이 호출 하나로 함께 만들어집니다(팀/개인은 같은 리포트의 두 화면입니다).
                    해당 프로젝트의 OWNER만 호출할 수 있습니다.

                    응답은 202이며 생성은 백그라운드에서 진행됩니다. 멤버 수만큼 LLM을 호출해 수십 초가
                    걸리므로 응답을 기다리지 않습니다. 프론트는 리포트 상세 조회 API를 폴링해
                    status가 COMPLETED로 바뀌는 것을 확인하세요.

                    이미 발행(COMPLETED)되었거나 실패(FAILED)한 리포트는 409입니다 —
                    이미 내려간 리포트의 내용이 나중에 바뀌면 안 되기 때문입니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "생성 시작됨(아직 완료 아님)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증이 없거나 유효하지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "프로젝트 OWNER가 아님",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "리포트 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 발행되었거나 실패한 리포트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<Void>> generateReport(
            Long userId,
            @Positive Long reportId
    );

    @Operation(
            summary = "리포트 PDF ZIP 다운로드 URL 발급",
            description = """
                    ACTIVE 프로젝트 멤버에게 팀 리포트 PDF 1개와 멤버별 개인 리포트 PDF 전체를
                    함께 담은 ZIP 파일의 임시 다운로드 URL을 발급합니다. 기존 경로 호환을 위해
                    엔드포인트는 /pdf를 유지합니다.
                    응답의 downloadUrl을 브라우저 이동/새 창 열기로 사용하면 파일 다운로드가 시작됩니다.
                    URL 만료 시간은 expiresInSeconds로 내려갑니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "다운로드 URL 발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 리포트 ID",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
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
                    description = "리포트 또는 PDF ZIP 없음",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "완료되지 않은 리포트",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "파일 저장소 비활성",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    ResponseEntity<ApiResponse<ReportPdfDownloadResponse>> createPdfDownloadUrl(
            Long userId,
            @Positive Long reportId
    );
}
