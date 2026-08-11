package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.dto.NotificationSettingsDto;
import com.plog.domain.notification.entity.NotificationGlobalSetting;
import com.plog.domain.notification.entity.NotificationProjectSetting;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.repository.NotificationGlobalSettingRepository;
import com.plog.domain.notification.repository.NotificationProjectSettingRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private NotificationGlobalSettingRepository globalRepository;
    @Mock private NotificationProjectSettingRepository projectSettingRepository;
    private NotificationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSettingsService(userRepository, projectRepository, projectMemberRepository,
                globalRepository, projectSettingRepository);
    }

    @Test
    void 저장값이_없는_유형과_프로젝트는_ON을_기본값으로_조회한다() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(10L);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember member = mock(ProjectMember.class);
        when(member.getProject()).thenReturn(project);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findAllByUserIdAndStatusOrderByIdAsc(1L, MemberStatus.ACTIVE))
                .thenReturn(List.of(member));
        when(globalRepository.findAllByUserId(1L)).thenReturn(List.of());
        when(projectSettingRepository.findAllByUserIdAndProjectIdIn(1L, List.of(10L))).thenReturn(List.of());

        NotificationSettingsDto.Response response = service.get(1L);

        assertThat(response.global()).hasSize(6)
                .containsEntry(NotificationType.INTEGRATION_COLLECTION_COMPLETED, true)
                .allSatisfy((type, enabled) -> assertThat(enabled).isTrue());
        assertThat(response.projects()).singleElement().satisfies(setting -> {
            assertThat(setting.projectId()).isEqualTo(10L);
            assertThat(setting.settings()).hasSize(6)
                    .containsEntry(NotificationType.INTEGRATION_COLLECTION_COMPLETED, true)
                    .allSatisfy((type, enabled) -> assertThat(enabled).isTrue());
        });
    }

    @Test
    void 전체_PATCH는_전달한_항목만_저장한다() {
        User user = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(globalRepository.findByUserIdAndType(1L, NotificationType.CHAT_MESSAGE)).thenReturn(Optional.empty());

        Map<NotificationType, Boolean> result = service.patchGlobal(1L, Map.of(NotificationType.CHAT_MESSAGE, false));

        verify(globalRepository).save(any(NotificationGlobalSetting.class));
        assertThat(result.get(NotificationType.CHAT_MESSAGE)).isFalse();
        assertThat(result.get(NotificationType.CHAT_MENTION)).isTrue();
    }

    @Test
    void 프로젝트_PATCH는_전달한_항목만_저장한다() {
        User user = mock(User.class);
        Project project = mock(Project.class);
        when(project.getProjectName()).thenReturn("Plog");
        ProjectMember member = mock(ProjectMember.class);
        when(member.getUser()).thenReturn(user);
        when(member.getProject()).thenReturn(project);
        when(projectRepository.existsById(10L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(projectSettingRepository.findAllByUserIdAndProjectIdIn(1L, List.of(10L))).thenReturn(List.of());
        when(projectSettingRepository.findByUserIdAndProjectIdAndType(1L, 10L, NotificationType.NOTICE))
                .thenReturn(Optional.empty());

        NotificationSettingsDto.ProjectSettings result = service.patchProject(
                1L, 10L, Map.of(NotificationType.NOTICE, false));

        verify(projectSettingRepository).save(any(NotificationProjectSetting.class));
        assertThat(result.settings().get(NotificationType.NOTICE)).isFalse();
        assertThat(result.settings().get(NotificationType.CHAT_MESSAGE)).isTrue();
    }

    @Test
    void 프로젝트_PATCH는_ACTIVE_멤버만_허용한다() {
        when(projectRepository.existsById(10L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchProject(
                1L, 10L, Map.of(NotificationType.CHAT_MESSAGE, false)))
                .isInstanceOf(ApiException.class);
    }
}
