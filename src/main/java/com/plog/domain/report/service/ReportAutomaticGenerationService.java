package com.plog.domain.report.service;

import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAutomaticGenerationService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectStatusService projectStatusService;

    public void generateIfEvaluationCompleted(Long evaluateeId) {
        Long projectId = projectMemberRepository.findProjectIdByMemberId(evaluateeId).orElse(null);
        if (projectId == null) {
            log.warn("peer_evaluation_report_project_not_found evaluateeId={}", evaluateeId);
            return;
        }
        projectStatusService.completeAndStartReportIfAllEvaluationsSubmitted(projectId);
    }
}
