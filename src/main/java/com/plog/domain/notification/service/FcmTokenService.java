package com.plog.domain.notification.service;

import com.plog.domain.notification.dto.FcmTokenDto;
import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.exception.NotificationErrorCode;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.notification.repository.NotificationUserRepository;
import com.plog.global.api.exception.ApiException;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmTokenService {
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationUserRepository userRepository;

    @Transactional
    public FcmTokenDto.Response put(Long userId, FcmTokenDto.Request request) {
        String value = request.token() == null ? "" : request.token().trim();
        if (value.isEmpty() || value.length() > 512) {
            throw new ApiException(NotificationErrorCode.INVALID_FCM_TOKEN);
        }
        if (!userRepository.existsById(userId)) {
            throw new ApiException(NotificationErrorCode.USER_NOT_FOUND);
        }
        fcmTokenRepository.upsert(userId, value);
        FcmToken token = fcmTokenRepository.findByToken(value).orElseThrow();
        return new FcmTokenDto.Response(
                token.getId(), token.getUser().getId(), token.getToken(),
                token.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    @Transactional
    public FcmTokenDto.DeletedResponse delete(Long userId, FcmTokenDto.Request request) {
        String value = request.token() == null ? "" : request.token().trim();
        if (value.isEmpty()) {
            throw new ApiException(NotificationErrorCode.INVALID_FCM_TOKEN);
        }
        boolean deleted = fcmTokenRepository.deleteByTokenAndUserId(value, userId) > 0;
        return new FcmTokenDto.DeletedResponse(deleted);
    }
}