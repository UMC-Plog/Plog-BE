package com.plog.domain.notification.service;

import com.plog.domain.notification.dto.response.NotificationResponse;
import com.plog.domain.notification.entity.Notification;
import com.plog.domain.notification.repository.NotificationRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.SliceResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public SliceResponse<NotificationResponse> getNotifications(Long userId, int page, int size) {
        if (userId == null) {
            throw new ApiException(AuthErrorCode.INVALID_TOKEN);
        }

        Slice<Notification> slice = notificationRepository.findSliceByUserId(userId, PageRequest.of(page, size));
        List<NotificationResponse> content = slice.getContent().stream()
                .map(NotificationResponse::from)
                .toList();
        return SliceResponse.of(slice, content);
    }
}