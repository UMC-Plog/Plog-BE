package com.plog.domain.task.controller;

import com.plog.domain.task.dto.response.TaskListResponse;
import com.plog.domain.task.service.TaskService;
import com.plog.global.api.response.ApiResponse;
import com.plog.global.api.response.TaskSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// TaskController와 URL 프리픽스가 달라서(/members/{projectMemberId}/tasks) 별도 컨트롤러로 분리.
// (ProjectJoinController, ProjectLeaveController 등과 동일한 컨벤션)
@Tag(name = "Task", description = "업무카드 API")
@RestController
@RequestMapping("/api/projects/{projectId}/members/{projectMemberId}/tasks")
public class ProjectMemberTaskController {

    private final TaskService taskService;

    public ProjectMemberTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(
            summary = "프로젝트 멤버 기준 업무카드 조회",
            description = """
                   특정 프로젝트 멤버가 담당자인 업무카드 목록을 조회합니다("내 업무" 탭 등에서 사용).
                   - 로그인 사용자가 해당 프로젝트의 활성 멤버가 아니면 접근할 수 없습니다.
                   - 조회 대상 projectMemberId도 해당 프로젝트의 활성(ACTIVE) 멤버여야 합니다.
                       프로젝트를 나간 멤버는 조회 대상이 아니며, 존재하지 않는 것과 동일하게 처리됩니다.
                   - 응답 형태는 업무카드 목록 조회(GET /tasks) API와 동일합니다.
                   - 인증 필요(Access Token).
                   """
    )
    @GetMapping
    public ApiResponse<TaskListResponse> getTasksByMember(
            @PathVariable Long projectId,
            @PathVariable Long projectMemberId,
            @AuthenticationPrincipal Long userId
    ) {
        TaskListResponse response = taskService.getTasksByMember(projectId, projectMemberId, userId);
        return ApiResponse.success(TaskSuccessCode.TASK_LIST_FOUND, response);
    }
}