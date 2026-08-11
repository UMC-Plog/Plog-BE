package com.plog.domain.project.event;

import static org.mockito.Mockito.verify;

import com.plog.domain.project.service.ProjectStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationCompletionCheckListenerTest {

    @Mock
    private ProjectStatusService projectStatusService;

    @InjectMocks
    private EvaluationCompletionCheckListener listener;

    @Test
    void checksProjectCompletionAfterSubmissionCommit() {
        listener.onEvaluationSubmitted(new EvaluationCompletionCheckRequestedEvent(1L));

        verify(projectStatusService).checkAndComplete(1L);
    }
}
