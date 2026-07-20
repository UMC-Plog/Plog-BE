package com.plog.domain.task.controller;

import com.plog.domain.task.dto.request.TaskCreateRequest;
import com.plog.domain.task.dto.response.TaskCreateResponse;
import com.plog.global.api.response.TaskSuccessCode;
import com.plog.domain.task.service.TaskService;
import com.plog.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Task", description = "업무카드 API")
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
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
            @AuthenticationPrincipal Long userId, // JwtAuthenticationFilter가 세팅한 principal(Long userId)
            @Valid @RequestBody TaskCreateRequest request
    ) {
        TaskCreateResponse response = taskService.createTask(projectId, userId, request);
        return ResponseEntity.status(TaskSuccessCode.TASK_CREATED.getHttpStatus())
                .body(ApiResponse.success(TaskSuccessCode.TASK_CREATED, response));
    }
}