package com.plog.domain.notification.service;

import com.plog.domain.notification.dto.NotificationSettingsDto;
import com.plog.domain.notification.entity.NotificationGlobalSetting;
import com.plog.domain.notification.entity.NotificationProjectSetting;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.exception.NotificationErrorCode;
import com.plog.domain.notification.repository.NotificationGlobalSettingRepository;
import com.plog.domain.notification.repository.NotificationProjectSettingRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.exception.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {
    private static final Set<NotificationType> PUSH_TYPES = EnumSet.of(
            NotificationType.CHAT_MESSAGE, NotificationType.CHAT_MENTION, NotificationType.NOTICE,
            NotificationType.PEER_EVALUATION_STARTED, NotificationType.REPORT_PUBLISHED,
            NotificationType.INTEGRATION_COLLECTION_COMPLETED);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final NotificationGlobalSettingRepository globalRepository;
    private final NotificationProjectSettingRepository projectSettingRepository;

    @Transactional(readOnly = true)
    public NotificationSettingsDto.Response get(Long userId) {
        requireUser(userId);
        List<ProjectMember> members = projectMemberRepository
                .findAllByUserIdAndStatusOrderByIdAsc(userId, MemberStatus.ACTIVE);
        Map<NotificationType, Boolean> global = defaultSettings();
        globalRepository.findAllByUserId(userId).forEach(setting ->
                global.put(setting.getType(), setting.isEnabled()));

        List<Long> projectIds = members.stream().map(member -> member.getProject().getId()).toList();
        Map<Long, Map<NotificationType, Boolean>> projectSettings = new LinkedHashMap<>();
        if (!projectIds.isEmpty()) {
            projectSettingRepository.findAllByUserIdAndProjectIdIn(userId, projectIds).forEach(setting ->
                    projectSettings.computeIfAbsent(setting.getProject().getId(), ignored -> defaultSettings())
                            .put(setting.getType(), setting.isEnabled()));
        }
        List<NotificationSettingsDto.ProjectSettings> projects = members.stream()
                .map(member -> new NotificationSettingsDto.ProjectSettings(
                        member.getProject().getId(), member.getProject().getProjectName(),
                        Map.copyOf(projectSettings.getOrDefault(member.getProject().getId(), defaultSettings()))))
                .toList();
        return new NotificationSettingsDto.Response(Map.copyOf(global), projects);
    }

    @Transactional
    public Map<NotificationType, Boolean> patchGlobal(Long userId, Map<NotificationType, Boolean> patch) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(NotificationErrorCode.USER_NOT_FOUND));
        validatePatch(patch);
        Map<NotificationType, Boolean> result = defaultSettings();
        globalRepository.findAllByUserId(userId).forEach(setting -> result.put(setting.getType(), setting.isEnabled()));
        patch.forEach((type, enabled) -> {
            NotificationGlobalSetting setting = globalRepository.findByUserIdAndType(userId, type)
                    .orElseGet(() -> NotificationGlobalSetting.create(user, type, enabled));
            setting.changeEnabled(enabled);
            globalRepository.save(setting);
            result.put(type, enabled);
        });
        return Map.copyOf(result);
    }

    @Transactional
    public NotificationSettingsDto.ProjectSettings patchProject(
            Long userId, Long projectId, Map<NotificationType, Boolean> patch) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND);
        }
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED));
        validatePatch(patch);
        Map<NotificationType, Boolean> result = defaultSettings();
        projectSettingRepository.findAllByUserIdAndProjectIdIn(userId, List.of(projectId))
                .forEach(setting -> result.put(setting.getType(), setting.isEnabled()));
        patch.forEach((type, enabled) -> {
            NotificationProjectSetting setting = projectSettingRepository
                    .findByUserIdAndProjectIdAndType(userId, projectId, type)
                    .orElseGet(() -> NotificationProjectSetting.create(
                            member.getUser(), member.getProject(), type, enabled));
            setting.changeEnabled(enabled);
            projectSettingRepository.save(setting);
            result.put(type, enabled);
        });
        return new NotificationSettingsDto.ProjectSettings(
                projectId, member.getProject().getProjectName(), Map.copyOf(result));
    }

    private void requireUser(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ApiException(NotificationErrorCode.USER_NOT_FOUND);
        }
    }

    private void validatePatch(Map<NotificationType, Boolean> patch) {
        if (patch == null || patch.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getValue() == null || !PUSH_TYPES.contains(entry.getKey()))) {
            throw new ApiException(NotificationErrorCode.INVALID_NOTIFICATION_SETTING);
        }
    }

    private Map<NotificationType, Boolean> defaultSettings() {
        EnumMap<NotificationType, Boolean> settings = new EnumMap<>(NotificationType.class);
        PUSH_TYPES.forEach(type -> settings.put(type, true));
        return settings;
    }
}
