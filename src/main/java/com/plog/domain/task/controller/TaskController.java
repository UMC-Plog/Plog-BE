package com.plog.domain.task.controller;

import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.response.TaskCreateResponse;
import com.plog.domain.task.dto.response.TaskDetailResponse;
import com.plog.domain.task.dto.response.TaskListResponse;
import com.plog.domain.task.dto.response.TaskSummaryResponse;
import com.plog.global.api.response.TaskSuccessCode;
import com.plog.domain.task.service.TaskService;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Task", description = "업무카드 API")
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(
            summary = "업무카드 생성",
            description = """
                    프로젝트 내 업무카드를 생성합니다.
                    - 로그인 사용자가 해당 프로젝트의 활성 멤버여야 합니다.
                    - 담당자(projectMemberId)는 같은 프로젝트의 "참여 중(ACTIVE)" 멤버여야 합니다.
                      다른 프로젝트 소속이면 TASK002, 이미 나간 멤버면 TASK003.
                    - 담당 영역(category)은 프로젝트 유형에 따라 선택 가능한 값이 다릅니다.
                      개발 프로젝트: PLANNING/DESIGN/DEVELOP/TEST_FIX/PRESENTATION_DOC/ETC
                      일반 팀프로젝트: RESEARCH/MATERIAL_PRODUCTION/PRESENTATION/SCHEDULE_MANAGEMENT/ETC
                      유형에 맞지 않으면 TASK004.
                    - 업무명은 2자 이상이어야 합니다.
                    - 첨부 자료(attachments)는 선택이며 생성 시점에 함께 등록할 수 있습니다.
                    - 인증 필요(Access Token).
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TaskCreateResponse>> createTask(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        TaskCreateResponse response = taskService.createTask(projectId, userId, request);
        return ResponseEntity.status(TaskSuccessCode.TASK_CREATED.getHttpStatus())
                .body(ApiResponse.success(TaskSuccessCode.TASK_CREATED, response));
    }

    @Operation(
            summary = "업무카드 목록 조회",
            description = """
                    현재 로그인한 사용자가 참여 중인 프로젝트의 업무카드 목록을 조회합니다.
                    - 로그인 사용자가 해당 프로젝트의 활성(ACTIVE) 멤버가 아니면 접근할 수 없습니다.
                    - 생성일(createdAt) 오름차순으로 정렬됩니다.
                    - 각 업무카드의 담당자 정보(담당자 ProjectMember ID, 닉네임, 프로필 url)를 함께 내려줍니다.
                    - 마감일이 지났고 상태가 완료(DONE)가 아니면 overdue = true 로 표시됩니다.
                    - 업무카드가 하나도 없으면 빈 배열을 반환합니다.
                    - 인증 필요(Access Token).
                    """
    )
    @GetMapping
    public ApiResponse<TaskListResponse> getTaskList(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Long userId
    ) {
        TaskListResponse response = taskService.getTaskList(projectId, userId);
        return ApiResponse.success(TaskSuccessCode.TASK_LIST_FOUND, response);
    }

    @Operation(
            summary = "업무카드 상세 조회",
            description = """
                업무카드 하나의 상세 정보를 조회합니다.
                - 로그인 사용자가 해당 프로젝트의 활성(ACTIVE) 멤버가 아니면 접근할 수 없습니다.
                - taskId가 존재하지 않거나 URL의 projectId 소속이 아니면 TASK007을 반환합니다.
                - 담당자 정보(닉네임, 프로필 이미지)를 assignee 객체로 함께 내려줍니다.
                - 첨부 산출물 전체 목록(파일명, 용량, 다운로드 URL)을 내려줍니다.
                - dDay: 마감일까지 남은 일수(음수면 지남).
                - isOverdue: 마감일이 지났고 완료(DONE)가 아닌 경우 true.
                - isImminent: 완료되지 않았고 마감일까지 D-3 이내(D-3~D-0)인 경우 true. isOverdue와는 배타적입니다.
                - completedAt은 상태가 완료(DONE)일 때만 값이 있습니다.
                - 인증 필요(Access Token).
                """
    )
    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> getTaskDetail(
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @AuthenticationPrincipal Long userId
    ) {
        TaskDetailResponse response = taskService.getTaskDetail(projectId, taskId, userId);
        return ApiResponse.success(TaskSuccessCode.TASK_DETAIL_FOUND, response);
    }
}
