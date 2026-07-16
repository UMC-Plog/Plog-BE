package com.plog.domain.notification.service;

import com.plog.domain.notification.dto.FcmTokenDto;
import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.exception.NotificationErrorCode;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.notification.repository.NotificationUserRepository;
import com.plog.domain.user.entity.User;
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(NotificationErrorCode.USER_NOT_FOUND));
        FcmToken token = fcmTokenRepository.findByToken(value).map(existing -> {
            fcmTokenRepository.updateOwnerAndTimestamp(existing.getId(), user.getId());
            return fcmTokenRepository.findByToken(value).orElseThrow();
        }).orElseGet(() -> fcmTokenRepository.saveAndFlush(FcmToken.builder().user(user).token(value).build()));
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
        fcmTokenRepository.findByToken(value)
                .filter(token -> token.getUser().getId().equals(userId))
                .ifPresent(fcmTokenRepository::delete);
        return new FcmTokenDto.DeletedResponse(true);
    }
}
