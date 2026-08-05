package com.plog.domain.report.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 생성 요청의 진입점. <b>검증은 동기, 생성은 비동기</b>로 나눈다 —
 * 잘못된 요청(권한 없음, 이미 발행됨)은 즉시 에러로 돌려주고, 오래 걸리는 생성만 던진다.
 * 전부 비동기로 넘기면 잘못된 요청도 202 를 받고 조용히 실패한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationLauncher {

    private final ReportRepository reportRepository;
    private final ProjectAccessService projectAccessService;
    private final ReportGenerationService reportGenerationService;

    /**
     * 리포트 생성을 시작시킨다. 반환 시점에는 아직 생성 중이며, 프론트는 리포트 상세 조회를
     * 폴링해 {@code status} 가 COMPLETED 로 바뀌는 것을 기다린다.
     * <p>
     * OWNER 로 제한하는 이유: 팀원 전원의 평가 텍스트를 다시 만드는 동작이라 아무나 부르면
     * 리포트 내용이 사람마다 다른 시점에 바뀔 수 있고, LLM 비용도 호출 수만큼 든다.
     */
    @Transactional(readOnly = true)
    public void launch(Long userId, Long reportId) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }
        Report report = reportRepository.findWithProjectById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        projectAccessService.requireOwner(report.getProject().getId(), userId);
        if (report.getStatus() != ReportStatus.GENERATING) {
            // 이미 발행됐거나 실패한 리포트를 다시 만들면 내려간 내용이 사후에 바뀐다.
            throw new ApiException(ReportErrorCode.REPORT_ALREADY_RESOLVED);
        }

        log.info("리포트 생성 요청: reportId={}, userId={}", reportId, userId);
        reportGenerationService.generateAsync(reportId);
    }
}
