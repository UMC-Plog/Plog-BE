package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.domain.project.entity.Project;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportTest {

    private final Project project = Project.builder()
            .projectName("Plog")
            .inviteTokenHash("invite-hash")
            .inviteTokenEncrypted("encrypted-invite")
            .build();

    @Test
    void startsInGeneratingStateWithoutCompletionMetadata() {
        Report report = Report.start(project);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATING);
        assertThat(report.getCompletedAt()).isNull();
        assertThat(report.getPdfObjectKey()).isNull();
        assertThat(report.getPdfFileName()).isNull();
    }

    @Test
    void completesAtTheProvidedTimeAndThenAttachesPdfMetadata() {
        Report report = Report.start(project);
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 21, 10, 0);

        report.complete(completedAt);
        report.attachPdf("reports/1/report.pdf", "Plog-report.pdf");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(report.getCompletedAt()).isEqualTo(completedAt);
        assertThat(report.getPdfObjectKey()).isEqualTo("reports/1/report.pdf");
        assertThat(report.getPdfFileName()).isEqualTo("Plog-report.pdf");
    }

    @Test
    void failureClearsCompletionAndPdfMetadata() {
        Report report = Report.start(project);

        report.fail();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.getCompletedAt()).isNull();
        assertThat(report.getPdfObjectKey()).isNull();
        assertThat(report.getPdfFileName()).isNull();
    }

    @Test
    void rejectsCompletionWithoutCompletionTime() {
        Report report = Report.start(project);

        assertThatThrownBy(() -> report.complete(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPdfMetadataBeforeCompletion() {
        Report report = Report.start(project);

        assertThatThrownBy(() -> report.attachPdf("reports/1/report.pdf", "report.pdf"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsafePdfMetadata() {
        Report report = Report.start(project);
        report.complete(LocalDateTime.of(2026, 7, 21, 10, 0));

        assertThatThrownBy(() -> report.attachPdf("https://cdn.test/report.pdf", "report.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> report.attachPdf("reports/1/report.pdf", "../report.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalStateCannotTransitionAgain() {
        Report completed = Report.start(project);
        completed.complete(LocalDateTime.of(2026, 7, 21, 10, 0));
        Report failed = Report.start(project);
        failed.fail();

        assertThatThrownBy(completed::fail).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.complete(LocalDateTime.of(2026, 7, 21, 11, 0)))
                .isInstanceOf(IllegalStateException.class);
    }
}
