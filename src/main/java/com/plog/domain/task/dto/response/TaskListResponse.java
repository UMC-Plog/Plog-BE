package com.plog.domain.task.dto.response;

import java.util.List;

// 목록 응답을 content 배열로 감싸기 위한 래퍼.
// (EvaluationTargetResponse가 targets 리스트를 감싸는 것과 동일한 패턴)
public record TaskListResponse(
        List<TaskSummaryResponse> content
) {
    public static TaskListResponse of(List<TaskSummaryResponse> content) {
        return new TaskListResponse(content);
    }
}