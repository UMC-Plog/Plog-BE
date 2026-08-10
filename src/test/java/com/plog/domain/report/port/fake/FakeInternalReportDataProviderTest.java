package com.plog.domain.report.port.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.TaskSummary;
import com.plog.domain.task.entity.TaskStatus;
import org.junit.jupiter.api.Test;

class FakeInternalReportDataProviderTest {

    @Test
    void 업무_목록에서_모든_통계와_비율을_일관되게_파생한다() {
        InternalReportData data = new FakeInternalReportDataProvider().provide(1L, 2L);

        int completed = (int) data.taskCardSummary().stream()
                .filter(task -> task.status() == TaskStatus.DONE)
                .count();
        int deadlineMet = (int) data.taskCardSummary().stream().filter(TaskSummary::metDeadline).count();
        int deadlineTarget = (int) data.taskCardSummary().stream()
                .filter(task -> task.deadline() != null)
                .count();

        assertThat(data.totalTaskCount()).isEqualTo(data.taskCardSummary().size());
        assertThat(data.completedTaskCount()).isEqualTo(completed);
        assertThat(data.deadlineMetTaskCount()).isEqualTo(deadlineMet);
        assertThat(data.deadlineTargetTaskCount()).isEqualTo(deadlineTarget);
        assertThat(data.completionRate()).isEqualTo(completed / (double) data.totalTaskCount());
        assertThat(data.deadlineComplianceRate()).isEqualTo(deadlineMet / (double) deadlineTarget);
    }
}
