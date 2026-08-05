package com.plog.domain.report.port;

import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import java.time.LocalDate;

/**
 * LLM 에 넘길 업무카드 1건 요약. 원본 Task 엔티티를 그대로 넘기지 않는 이유는
 * (1) LLM 프롬프트에 필요한 필드만 추리고 (2) 리포트 도메인이 task 도메인 변경에 끌려다니지 않기 위해서다.
 *
 * @param title       업무카드 제목. 프롬프트에 그대로 실리므로 사용자 입력이 노출된다는 점에 유의
 * @param category    업무 영역
 * @param status      마감 시점의 카드 상태
 * @param deadline    마감일. 없을 수 있다
 * @param metDeadline 기한 내 완료 여부. 완료되지 않았거나 마감일이 없으면 false
 */
public record TaskSummary(
        String title,
        TaskCategory category,
        TaskStatus status,
        LocalDate deadline,
        boolean metDeadline
) {
}
