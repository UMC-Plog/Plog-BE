package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.dto.request.AgreementItem;
import com.plog.domain.user.dto.request.SocialSignupRequest;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.SocialSignupTicket;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserAgreementRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SocialSignupServiceTest {

    private UserRepository userRepository;
    private SocialSignupTicketService ticketService;
    private TokenIssuer tokenIssuer;
    private SocialSignupService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        ticketService = mock(SocialSignupTicketService.class);
        tokenIssuer = mock(TokenIssuer.class);
        UserAgreementService userAgreementService =
                new UserAgreementService(mock(UserAgreementRepository.class));
        service = new SocialSignupService(userRepository, ticketService, userAgreementService, tokenIssuer);

        lenient().when(ticketService.consumeOrThrow("raw-ticket")).thenReturn(SocialSignupTicket.issue(
                ProviderType.KAKAO, "kakao-1", "a@plog.test", "hash",
                LocalDateTime.of(2026, 7, 26, 13, 0)));
        lenient().when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(tokenIssuer.issue(any())).thenReturn(new TokenResponse("access", "refresh"));
    }

    private SocialSignupRequest request(String nickname) {
        return new SocialSignupRequest("raw-ticket", "홍길동", nickname, ProfilePreset.PENGUIN,
                List.of(new AgreementItem(AgreementType.SERVICE_TERMS, true),
                        new AgreementItem(AgreementType.PRIVACY, true),
                        new AgreementItem(AgreementType.EXTERNAL_DATA, true),
                        new AgreementItem(AgreementType.MARKETING, false)));
    }

    @Test
    @DisplayName("티켓의 이메일·provider로 소셜 유저를 만들고 토큰을 발급한다")
    void createsSocialUser() {
        given(userRepository.existsByNickname("조이")).willReturn(false);

        TokenResponse tokens = service.signup(request("조이"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo("a@plog.test");
        assertThat(saved.getProviderType()).isEqualTo(ProviderType.KAKAO);
        assertThat(saved.getProviderId()).isEqualTo("kakao-1");
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getProfilePreset()).isEqualTo(ProfilePreset.PENGUIN);
        assertThat(tokens.accessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("닉네임이 중복이면 NICKNAME_DUPLICATED로 끊고 유저를 만들지 않는다")
    void rejectsDuplicatedNickname() {
        given(userRepository.existsByNickname("조이")).willReturn(true);

        assertThatThrownBy(() -> service.signup(request("조이")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.NICKNAME_DUPLICATED);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("약관 검증은 티켓 소비 전에 한다 (실패해도 티켓이 살아있어야 재시도할 수 있다)")
    void validatesAgreementsBeforeConsumingTicket() {
        SocialSignupRequest missingRequired = new SocialSignupRequest(
                "raw-ticket", "홍길동", "조이", null,
                List.of(new AgreementItem(AgreementType.SERVICE_TERMS, true)));

        assertThatThrownBy(() -> service.signup(missingRequired))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REQUIRED_AGREEMENT_MISSING);
        verify(ticketService, never()).consumeOrThrow(any());
    }

    @Test
    @DisplayName("티켓이 무효하면 그 예외를 그대로 올린다")
    void propagatesTicketFailure() {
        given(ticketService.consumeOrThrow("raw-ticket"))
                .willThrow(new ApiException(AuthErrorCode.SOCIAL_TICKET_EXPIRED));

        assertThatThrownBy(() -> service.signup(request("조이")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SOCIAL_TICKET_EXPIRED);
    }
}
