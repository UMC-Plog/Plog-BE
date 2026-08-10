package com.plog.domain.report.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectStatusService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportAutomaticGenerationServiceTest {

    @Mock
    private ProjectMemberRepository memberRepository;
    @Mock
    private ProjectStatusService projectStatusService;
    @InjectMocks
    private ReportAutomaticGenerationService service;

    @Test
    void checksProjectCompletionAfterPeerSubmission() {
        when(memberRepository.findProjectIdByMemberId(7L)).thenReturn(Optional.of(3L));

        service.generateIfEvaluationCompleted(7L);

        verify(projectStatusService).completeAndStartReportIfAllEvaluationsSubmitted(3L);
    }

    @Test
    void ignoresDeletedEvaluatee() {
        when(memberRepository.findProjectIdByMemberId(7L)).thenReturn(Optional.empty());

        service.generateIfEvaluationCompleted(7L);

        verifyNoInteractions(projectStatusService);
    }
}
