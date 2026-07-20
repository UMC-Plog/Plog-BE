package com.plog.domain.project.service;

import com.plog.domain.project.dto.response.ProjectListResponse;
import com.plog.domain.project.dto.response.ProjectListResponse.MemberPreview;
import com.plog.domain.project.dto.response.ProjectListResponse.ProjectSummary;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.domain.task.repository.TaskRepository.ProjectTaskProgress;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectListService {

    private static final int MEMBER_PREVIEW_LIMIT = 3;

    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;

    public ProjectListService(
            ProjectMemberRepository projectMemberRepository,
            TaskRepository taskRepository
    ) {
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public ProjectListResponse getProjects(Long userId, ProjectStatus status, int page, int size) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        Page<ProjectMember> membershipPage = projectMemberRepository.findProjectPage(
                userId,
                MemberStatus.ACTIVE,
                status,
                PageRequest.of(page, size)
        );
        List<Project> projects = membershipPage.getContent().stream()
                .map(ProjectMember::getProject)
                .toList();

        if (projects.isEmpty()) {
            return response(membershipPage, List.of());
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, List<ProjectMember>> membersByProject = projectMemberRepository
                .findActiveMembers(projectIds, MemberStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(member -> member.getProject().getId()));
        Map<Long, ProjectTaskProgress> progressByProject = taskRepository
                .findProgressByProjectIds(projectIds, TaskStatus.DONE)
                .stream()
                .collect(Collectors.toMap(ProjectTaskProgress::getProjectId, progress -> progress));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<ProjectSummary> summaries = projects.stream()
                .map(project -> summary(
                        project,
                        membersByProject.getOrDefault(project.getId(), List.of()),
                        progressByProject.get(project.getId()),
                        today
                ))
                .toList();
        return response(membershipPage, summaries);
    }

    private ProjectSummary summary(
            Project project,
            List<ProjectMember> members,
            ProjectTaskProgress progress,
            LocalDate today
    ) {
        List<MemberPreview> previews = members.stream()
                .limit(MEMBER_PREVIEW_LIMIT)
                .map(member -> new MemberPreview(
                        member.getUser().getId(),
                        member.getUser().getNickname(),
                        member.getUser().getProfileImageUrl()
                ))
                .toList();
        int memberCount = members.size();

        return new ProjectSummary(
                project.getId(),
                project.getProjectName(),
                project.getProjectType(),
                project.getStatus(),
                project.getEndDay(),
                ChronoUnit.DAYS.between(today, project.getEndDay()),
                memberCount,
                previews,
                Math.max(memberCount - previews.size(), 0),
                progressPercent(progress)
        );
    }

    private int progressPercent(ProjectTaskProgress progress) {
        if (progress == null || progress.getTotalCount() == 0) {
            return 0;
        }
        return (int) (progress.getDoneCount() * 100 / progress.getTotalCount());
    }

    private ProjectListResponse response(Page<ProjectMember> page, List<ProjectSummary> content) {
        return new ProjectListResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
