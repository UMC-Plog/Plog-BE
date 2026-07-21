package com.plog.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.notification.dto.FcmTokenDto;
import com.plog.domain.notification.entity.FcmToken;
import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.notification.repository.NotificationUserRepository;
import com.plog.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {
    @Mock
    private FcmTokenRepository fcmTokenRepository;
    @Mock
    private NotificationUserRepository userRepository;

    private FcmTokenService service;

    @BeforeEach
    void setUp() {
        service = new FcmTokenService(fcmTokenRepository, userRepository);
    }

    @Test
    void registersTrimmedTokenAndReturnsOwner() {
        User user = mock(User.class);
        FcmToken token = mock(FcmToken.class);
        when(userRepository.existsById(7L)).thenReturn(true);
        when(fcmTokenRepository.findByToken("device-token")).thenReturn(Optional.of(token));
        when(token.getId()).thenReturn(3L);
        when(token.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(token.getToken()).thenReturn("device-token");
        when(token.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 7, 21, 1, 0));

        FcmTokenDto.Response response = service.put(7L, new FcmTokenDto.Request(" device-token "));

        verify(fcmTokenRepository).upsert(7L, "device-token");
        assertThat(response.fcmId()).isEqualTo(3L);
        assertThat(response.userId()).isEqualTo(7L);
        assertThat(response.token()).isEqualTo("device-token");
    }

    @Test
    void deletionIsIdempotentAndAlwaysReportsDeleted() {
        when(userRepository.existsById(7L)).thenReturn(true);
        when(fcmTokenRepository.deleteByTokenAndUserId("missing-token", 7L)).thenReturn(0);

        FcmTokenDto.DeletedResponse response = service.delete(
                7L, new FcmTokenDto.Request("missing-token"));

        assertThat(response.deleted()).isTrue();
    }
}
