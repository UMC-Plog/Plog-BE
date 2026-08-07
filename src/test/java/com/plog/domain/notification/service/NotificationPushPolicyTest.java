package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.entity.NotificationGlobalSetting;
import com.plog.domain.notification.entity.NotificationProjectSetting;
import com.plog.domain.notification.entity.NotificationType;
import com.plog.domain.notification.repository.NotificationGlobalSettingRepository;
import com.plog.domain.notification.repository.NotificationProjectSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationPushPolicyTest {
    @Mock private NotificationGlobalSettingRepository globalRepository;
    @Mock private NotificationProjectSettingRepository projectRepository;

    @Test
    void 저장된_설정이_없으면_push를_허용한다() {
        NotificationPushPolicy policy = new NotificationPushPolicy(globalRepository, projectRepository);
        when(globalRepository.findByUserIdAndType(1L, NotificationType.CHAT_MESSAGE)).thenReturn(Optional.empty());
        when(projectRepository.findByUserIdAndProjectIdAndType(1L, 10L, NotificationType.CHAT_MESSAGE))
                .thenReturn(Optional.empty());

        assertThat(policy.isEnabled(1L, 10L, NotificationType.CHAT_MESSAGE)).isTrue();
    }

    @Test
    void 전체_설정이_OFF면_프로젝트_설정과_무관하게_push를_막는다() {
        NotificationPushPolicy policy = new NotificationPushPolicy(globalRepository, projectRepository);
        NotificationGlobalSetting global = org.mockito.Mockito.mock(NotificationGlobalSetting.class);
        when(global.isEnabled()).thenReturn(false);
        when(globalRepository.findByUserIdAndType(1L, NotificationType.CHAT_MESSAGE))
                .thenReturn(Optional.of(global));

        assertThat(policy.isEnabled(1L, 10L, NotificationType.CHAT_MESSAGE)).isFalse();
        verifyNoInteractions(projectRepository);
    }

    @Test
    void 프로젝트_설정이_OFF면_push를_막는다() {
        NotificationPushPolicy policy = new NotificationPushPolicy(globalRepository, projectRepository);
        NotificationProjectSetting project = org.mockito.Mockito.mock(NotificationProjectSetting.class);
        when(project.isEnabled()).thenReturn(false);
        when(globalRepository.findByUserIdAndType(1L, NotificationType.CHAT_MESSAGE)).thenReturn(Optional.empty());
        when(projectRepository.findByUserIdAndProjectIdAndType(1L, 10L, NotificationType.CHAT_MESSAGE))
                .thenReturn(Optional.of(project));

        assertThat(policy.isEnabled(1L, 10L, NotificationType.CHAT_MESSAGE)).isFalse();
    }
}
