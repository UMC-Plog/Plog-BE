package com.plog.domain.project.service;

import com.plog.domain.project.dto.request.ProjectCreateRequest;
import com.plog.domain.project.dto.response.ProjectCreateResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ProjectCreateService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final InviteTokenService inviteTokenService;
    private final String inviteBaseUrl;

    public ProjectCreateService(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            InviteTokenService inviteTokenService,
            @Value("${plog.invite.base-url}") String inviteBaseUrl
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.inviteTokenService = inviteTokenService;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    public ProjectCreateResponse create(Long userId, ProjectCreateRequest request) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        String projectName = normalizeProjectName(request.projectName());
        LocalDate startDay = LocalDate.now(ZoneOffset.UTC);
        validateEndDay(request.endDay(), startDay);

        InviteTokenService.IssuedResult<CreatedProject> issuedResult =
                inviteTokenService.issueAndPersist(issuedToken -> {
                    User creator = userRepository.findById(userId)
                            .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_TOKEN));
                    Project project = projectRepository.save(Project.builder()
                            .projectName(projectName)
                            .inviteTokenHash(issuedToken.hash())
                            .inviteTokenEncrypted(issuedToken.encryptedValue())
                            .projectType(request.projectType())
                            .status(ProjectStatus.IN_PROGRESS)
                            .startDay(startDay)
                            .endDay(request.endDay())
                            .build());
                    ProjectMember creatorMember = projectMemberRepository.save(ProjectMember.builder()
                            .user(creator)
                            .project(project)
                            .role(ProjectRole.OWNER)
                            .status(MemberStatus.ACTIVE)
                            .build());
                    return new CreatedProject(project, creatorMember);
                });

        CreatedProject created = issuedResult.value();
        String inviteCode = issuedResult.token().rawValue();
        return new ProjectCreateResponse(
                created.project().getId(),
                created.project().getProjectName(),
                created.project().getProjectType(),
                created.project().getStatus(),
                created.project().getStartDay(),
                created.project().getEndDay(),
                created.member().getId(),
                created.member().getRole(),
                new ProjectCreateResponse.Invite(
                        inviteCode,
                        UriComponentsBuilder.fromUriString(inviteBaseUrl)
                                .pathSegment(inviteCode)
                                .toUriString()
                )
        );
    }

    private String normalizeProjectName(String projectName) {
        if (projectName == null) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_NAME);
        }
        String normalizedName = projectName.trim();
        if (normalizedName.length() < 2 || normalizedName.length() > 20) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_NAME);
        }
        return normalizedName;
    }

    private void validateEndDay(LocalDate endDay, LocalDate startDay) {
        if (endDay == null || !endDay.isAfter(startDay)) {
            throw new ApiException(ProjectErrorCode.INVALID_PROJECT_END_DAY);
        }
    }

    private record CreatedProject(Project project, ProjectMember member) {
    }
}
