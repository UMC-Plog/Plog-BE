package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.repository.NotificationRepository;
import com.plog.global.api.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationQueryService notificationQueryService;

    @Test
    void userId가_없으면_예외() {
        assertThrows(ApiException.class, () -> notificationQueryService.getNotifications(null, 0, 20));
    }

    @Test
    void 알림이_없으면_빈_목록을_반환한다() {
        when(notificationRepository.findSliceByUserId(1L, PageRequest.of(0, 20)))
                .thenReturn(new SliceImpl<>(List.of()));

        var response = notificationQueryService.getNotifications(1L, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }
}