package com.plog.domain.project.service;

import com.plog.domain.project.dto.request.ProjectJoinRequest;
import com.plog.domain.project.dto.response.ProjectJoinResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectJoinService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final InviteTokenService inviteTokenService;

    public ProjectJoinService(
            UserRepository userRepository,
            ProjectMemberRepository projectMemberRepository,
            InviteTokenService inviteTokenService
    ) {
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.inviteTokenService = inviteTokenService;
    }

    @Transactional
    public ProjectJoinResponse join(Long userId, ProjectJoinRequest request) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_TOKEN));
        Project project = inviteTokenService.findProjectByRawToken(request.inviteCode())
                .orElseThrow(() -> new ApiException(ProjectErrorCode.INVALID_INVITE_CODE));

        JoinedMember joinedMember = projectMemberRepository
                .findByProjectIdAndUserIdForUpdate(project.getId(), userId)
                .map(this::reactivate)
                .orElseGet(() -> createMember(user, project));

        return toResponse(project, joinedMember);
    }

    private JoinedMember reactivate(ProjectMember member) {
        if (member.getStatus() == MemberStatus.ACTIVE) {
            throw new ApiException(ProjectErrorCode.PROJECT_ALREADY_JOINED);
        }
        member.reactivateAsMember();
        ProjectMember savedMember = projectMemberRepository.saveAndFlush(member);
        return new JoinedMember(savedMember, savedMember.getUpdatedAt());
    }

    private JoinedMember createMember(User user, Project project) {
        try {
            ProjectMember savedMember = projectMemberRepository.saveAndFlush(ProjectMember.builder()
                    .user(user)
                    .project(project)
                    .role(ProjectRole.MEMBER)
                    .status(MemberStatus.ACTIVE)
                    .build());
            return new JoinedMember(savedMember, savedMember.getCreatedAt());
        } catch (DataIntegrityViolationException exception) {
            if (isProjectMemberUniqueViolation(exception)) {
                throw new ApiException(ProjectErrorCode.PROJECT_ALREADY_JOINED);
            }
            throw exception;
        }
    }

    private boolean isProjectMemberUniqueViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && ProjectMember.UNIQUE_PROJECT_MEMBER_CONSTRAINT.equalsIgnoreCase(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            if (cause instanceof PSQLException postgresException
                    && isProjectMemberUniqueViolation(postgresException)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isProjectMemberUniqueViolation(PSQLException exception) {
        ServerErrorMessage serverError = exception.getServerErrorMessage();
        return PSQLState.UNIQUE_VIOLATION.getState().equals(exception.getSQLState())
                && serverError != null
                && ProjectMember.UNIQUE_PROJECT_MEMBER_CONSTRAINT.equalsIgnoreCase(
                        serverError.getConstraint());
    }

    private ProjectJoinResponse toResponse(Project project, JoinedMember joinedMember) {
        ProjectMember member = joinedMember.member();
        return new ProjectJoinResponse(
                project.getId(),
                project.getProjectName(),
                member.getId(),
                member.getRole(),
                project.getStatus(),
                member.getStatus(),
                joinedMember.joinedAt()
        );
    }

    private record JoinedMember(ProjectMember member, LocalDateTime joinedAt) {
    }
}
