package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskOverdueCalculator;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.report.entity.CompetencyCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TaskSummaryResponse(
        Long taskId,
        String title,
        TaskCategory category,
        TaskStatus cardStatus,
        LocalDate endDate,
        boolean isOverdue, // 저장값 아님 — 응답 시점에 계산 (endDate 지났는데 DONE이 아니면 true)
        @Schema(description = "업무 제목으로 추론한 예상 역량. 실제 역량 발휘 증거가 아니며 분류 실패 시 null")
        CompetencyCategory inferredCompetency,
        @Schema(description = "확률이 아닌 선택된 anchor와의 코사인 유사도(0~1). 내부 판단값으로 사용")
        BigDecimal competencyConfidence,
        @Schema(description = "업무 제목 역량 분류 anchor/규칙 버전. 분류 실패 시 null")
        String competencyClassifierVersion,
        AssigneeResponse assignee,
        int attachmentCount
) {

    public record AssigneeResponse(
            Long projectMemberId,
            String nickname,
            ProfilePreset profilePreset // ChatChannelParticipantResponse/ProjectListResponse와 동일한 필드명 컨벤션
    ) {
        public static AssigneeResponse from(Task task) {
            return new AssigneeResponse(
                    task.getProjectMember().getId(),
                    task.getProjectMember().getDisplayNickname(),
                    task.getProjectMember().getUser().getProfilePreset()
            );
        }
    }

    public static TaskSummaryResponse from(Task task, int attachmentCount) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getTitle(),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                TaskOverdueCalculator.isOverdue(task),
                task.getInferredCompetency(),
                task.getCompetencyConfidence(),
                task.getCompetencyClassifierVersion(),
                AssigneeResponse.from(task),
                attachmentCount
        );
    }
}
