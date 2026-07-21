package com.plog.domain.project.service;

import com.plog.domain.integration.entity.ExternalConnection;
import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.domain.project.repository.ProjectExternalConnectionRepository;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectSettingsService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectExternalConnectionRepository externalConnectionRepository;
    private final InviteTokenCipher inviteTokenCipher;

    @Value("${plog.invite.base-url}")
    private String inviteBaseUrl;

    public ProjectSettingsDto.Response getSettings(Long projectId, Long userId) {
        Project project = requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        String token = inviteTokenCipher.decrypt(project.getInviteTokenEncrypted());
        String inviteUrl = inviteBaseUrl + "/" + token;
        List<ProjectSettingsDto.ExternalConnection> connections = externalConnectionRepository
                .findAllByProjectMemberIdOrderByLinkTypeAsc(member.getId()).stream()
                .map(this::toConnection)
                .toList();
        return new ProjectSettingsDto.Response(
                project.getId(), project.getProjectName(), project.getProjectType(), project.getStatus().name(),
                project.getStartDay(), project.getEndDay(),
                new ProjectSettingsDto.Invite(inviteUrl, inviteUrl + "/qr"), connections,
                TimeUtil.toInstant(project.getUpdatedAt()));
    }

    @Transactional
    public ProjectSettingsDto.UpdateResponse updateSettings(
            Long projectId,
            Long userId,
            ProjectSettingsDto.UpdateRequest request
    ) {
        Project project = requireProject(projectId);
        requireOwner(projectId, userId);
        String projectName = request.projectName() == null ? null : request.projectName().trim();
        if (projectName != null && (projectName.length() < 2 || projectName.length() > 20)) {
            throw new ApiException(ProjectApiErrorCode.VALIDATION_ERROR);
        }
        if (request.endDay() != null
                && (!request.endDay().isAfter(TimeUtil.todayUtc())
                || !request.endDay().isAfter(project.getStartDay()))) {
            throw new ApiException(ProjectApiErrorCode.VALIDATION_ERROR);
        }
        project.updateSettings(projectName, request.endDay(), request.projectType());
        projectRepository.saveAndFlush(project);
        return new ProjectSettingsDto.UpdateResponse(
                project.getId(), project.getProjectName(), project.getProjectType(), project.getEndDay(),
                TimeUtil.toInstant(project.getUpdatedAt()));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectMember requireActiveMember(Long projectId, Long userId) {
        if (userId == null) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED);
        }
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED));
        if (member.getRole() != ProjectRole.OWNER && member.getRole() != ProjectRole.MEMBER) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED);
        }
        return member;
    }

    private ProjectMember requireOwner(Long projectId, Long userId) {
        ProjectMember member = requireActiveMember(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_SETTING_PERMISSION_DENIED);
        }
        return member;
    }

    private ProjectSettingsDto.ExternalConnection toConnection(ExternalConnection connection) {
        return new ProjectSettingsDto.ExternalConnection(
                connection.getId(), connection.getLinkType().name(), connection.isLinked());
    }
}
