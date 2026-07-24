package com.plog.domain.report.controller.docs;

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

@Tag(name = "Report", description = "리포트 API")
public interface ReportControllerDoc {

    @Operation(
            summary = "리포트 검색",
            description = """
                    ACTIVE 멤버십의 프로젝트 리포트를 프로젝트명과 완료 기간으로 Slice 검색합니다.
                    keyword는 프로젝트명 검색에 사용하고, startDate/endDate는 리포트 완료일 범위 필터입니다.
                    프론트는 hasNext=true일 때 다음 page를 요청하면 됩니다.
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
            summary = "리포트 PDF 다운로드 URL 발급",
            description = """
                    ACTIVE 프로젝트 멤버에게 완료된 리포트 PDF의 임시 다운로드 URL을 발급합니다.
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
                    description = "리포트 또는 PDF 없음",
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
