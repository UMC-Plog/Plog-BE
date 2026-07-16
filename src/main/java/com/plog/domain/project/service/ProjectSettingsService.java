package com.plog.domain.project.service;

import com.plog.domain.integration.entity.ExternalConnection;
import com.plog.domain.project.dto.ProjectSettingsDto;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.exception.ProjectErrorCode;
import com.plog.domain.project.repository.ProjectExternalConnectionRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final ProjectExternalConnectionRepository externalConnectionRepository;
    private final ProjectAccessService projectAccessService;
    private final InviteTokenCipher inviteTokenCipher;

    @Value("${plog.invite.base-url:https://plog.example.com/invite}")
    private String inviteBaseUrl;

    public ProjectSettingsDto.Response getSettings(Long projectId, Long userId) {
        Project project = requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
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
                project.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    @Transactional
    public ProjectSettingsDto.UpdateResponse updateSettings(
            Long projectId,
            Long userId,
            ProjectSettingsDto.UpdateRequest request
    ) {
        projectAccessService.requireOwner(projectId, userId);
        Project project = requireProject(projectId);
        String projectName = request.projectName() == null ? null : request.projectName().trim();
        if (projectName != null && (projectName.length() < 2 || projectName.length() > 20)) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_NAME);
        }
        if (request.endDay() != null
                && (!request.endDay().isAfter(LocalDate.now(ZoneOffset.UTC))
                || !request.endDay().isAfter(project.getStartDay()))) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_END_DAY);
        }
        project.updateSettings(projectName, request.endDay(), request.projectType());
        projectRepository.saveAndFlush(project);
        return new ProjectSettingsDto.UpdateResponse(
                project.getId(), project.getProjectName(), project.getProjectType(), project.getEndDay(),
                project.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    private ProjectSettingsDto.ExternalConnection toConnection(ExternalConnection connection) {
        return new ProjectSettingsDto.ExternalConnection(
                connection.getId(), connection.getLinkType().name(), connection.isLinked());
    }
}
