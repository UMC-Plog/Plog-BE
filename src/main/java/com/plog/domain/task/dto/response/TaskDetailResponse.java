package com.plog.domain.task.dto.response;

import com.plog.domain.task.entity.*;
import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import com.plog.domain.report.entity.CompetencyCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record TaskDetailResponse(
        Long taskId,
        String title,
        AssigneeResponse assignee,
        TaskCategory category,
        TaskStatus cardStatus,
        LocalDate endDate,
        LocalDateTime completedAt, // 상태가 DONE일 때만 값 존재, 그 외 null
        int dDay,        // endDate - 오늘. 마감일이 지났으면 음수. 배너 문구는 프론트에서 조립
        boolean isOverdue,
        boolean isImminent, // 마감 D-3 ~ D-0, 완료되지 않은 경우에만 true
        @Schema(description = "업무 제목으로 추론한 예상 역량. 실제 역량 발휘 증거가 아니며 분류 실패 시 null")
        CompetencyCategory inferredCompetency,
        @Schema(description = "확률이 아닌 선택된 anchor와의 코사인 유사도(0~1). 내부 판단값으로 사용")
        BigDecimal competencyConfidence,
        @Schema(description = "업무 제목 역량 분류 anchor/규칙 버전. 분류 실패 시 null")
        String competencyClassifierVersion,
        List<AttachmentResponse> attachments
) {

    // 마감 임박 기준: 마감일 3일 전부터 (기획 확정값, 화면 배너 표시 기준과 동일)
    private static final int IMMINENT_THRESHOLD_DAYS = 3;

    public record AssigneeResponse(
            Long projectMemberId,
            String nickname,
            ProfilePreset profilePreset
    ) {
        public static AssigneeResponse from(Task task) {
            return new AssigneeResponse(
                    task.getProjectMember().getId(),
                    task.getProjectMember().getDisplayNickname(),
                    task.getProjectMember().getUser().getProfilePreset()
            );
        }
    }

    public record AttachmentResponse(
            Long taskAttachmentId,
            AttachmentType attachmentType,
            Long fileId,
            String fileName,
            Long fileSize, // 바이트 단위. "5.2MB" 같은 표시 변환은 프론트 담당
            @Schema(description = "LINK 첨부의 외부 링크. FILE 이면 null")
            String linkUrl,
            @Schema(description = "FILE 첨부의 다운로드 URL 발급 API 주소. 클릭 시 이 주소를 "
                    + "호출해 presigned 를 받는다. LINK 면 null. "
                    + "이 주소를 <a href> 에 걸면 JSON 이 보인다")
            String downloadUrlApi
    ) {
        public static AttachmentResponse of(TaskAttachment attachment, String downloadUrlApi) {
            return new AttachmentResponse(
                    attachment.getId(),
                    attachment.getAttachmentType(),
                    attachment.getUploadedFile() == null
                            ? null : attachment.getUploadedFile().getId(),
                    attachment.displayName(),
                    attachment.getAttachmentType() == AttachmentType.FILE
                        ? attachment.getFileSize()
                            : null,
                    attachment.getLinkUrl(),
                    downloadUrlApi
            );
        }
    }

    public static TaskDetailResponse from(Task task, List<AttachmentResponse> attachments) {
        int dDay = calculateDDay(task);
        boolean overdue = TaskOverdueCalculator.isOverdue(task);
        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                AssigneeResponse.from(task),
                task.getCategory(),
                task.getCardStatus(),
                task.getEndDate(),
                task.getCardStatus() == TaskStatus.DONE ? task.getCompletedAt() : null,
                dDay,
                overdue,
                isImminent(task, dDay, overdue),
                task.getInferredCompetency(),
                task.getCompetencyConfidence(),
                task.getCompetencyClassifierVersion(),
                attachments
        );
    }

    private static int calculateDDay(Task task) {
        if (task.getEndDate() == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), task.getEndDate());
    }

    // 마감이 이미 지난 경우(overdue)는 임박이 아니라 초과이므로 배타적으로 처리
    private static boolean isImminent(Task task, int dDay, boolean overdue) {
        return task.getEndDate() != null
                && task.getCardStatus() != TaskStatus.DONE
                && !overdue
                && dDay <= IMMINENT_THRESHOLD_DAYS;
    }
}
