package com.plog.domain.task.entity;

import com.plog.global.util.TimeUtil;
import java.time.LocalDate;

/**
 * 업무카드 마감초과(isOverdue) 판정 로직.
 * 마감일 초과는 별도 상태값이 아니라 조회 시점에 계산한다.
 * 진행중/예정 시절 마감초과였다는 사실은 완료 후에도 유지되어야 하므로,
 * DONE인 경우 completedAt이 마감일 이후인지로 판단한다.
 * {@link TaskSummaryResponse}, {@link TaskDetailResponse} 양쪽에서 공통으로 사용한다.
 */
public final class TaskOverdueCalculator {

    private TaskOverdueCalculator() {
    }

    public static boolean isOverdue(Task task) {
        if (task.getEndDate() == null) {
            return false;
        }
        if (task.getCardStatus() == TaskStatus.DONE) {
            return isCompletedAfterDeadline(task);
        }
        return task.getEndDate().isBefore(LocalDate.now());
    }

    // completedAt은 UTC 저장값이라 마감일(KST 달력 기준)과 비교하려면 표시 타임존으로 환산해야 한다.
    private static boolean isCompletedAfterDeadline(Task task) {
        if (task.getCompletedAt() == null) {
            return false;
        }
        LocalDate completedDate = task.getCompletedAt()
                .atZone(TimeUtil.STORAGE_ZONE)
                .withZoneSameInstant(TimeUtil.DISPLAY_ZONE)
                .toLocalDate();
        return completedDate.isAfter(task.getEndDate());
    }
}