package com.plog.domain.project.service;

import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import org.springframework.stereotype.Component;

@Component
class ProjectSettingsValidator {

    void validate(Project project, ProjectSettingsDto.UpdateRequest request) {
        validateProjectName(request.projectName());
        validateEndDay(project, request);
    }

    private void validateProjectName(String projectName) {
        if (projectName == null) {
            return;
        }
        int length = projectName.trim().length();
        if (length < 2 || length > 20) {
            throw new ApiException(ProjectApiErrorCode.VALIDATION_ERROR);
        }
    }

    private void validateEndDay(Project project, ProjectSettingsDto.UpdateRequest request) {
        if (request.endDay() != null
                && (!request.endDay().isAfter(TimeUtil.todayUtc())
                || !request.endDay().isAfter(project.getStartDay()))) {
            throw new ApiException(ProjectApiErrorCode.VALIDATION_ERROR);
        }
    }
}
