package com.plog.domain.user.service;

import com.plog.domain.user.dto.response.SocialLoginResponse;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 소셜 인증 결과를 로그인 또는 가입 대기로 가른다.
 * <p>
 * 의도적으로 클래스/메서드 레벨 @Transactional 을 걸지 않는다. provider 호출(외부 I/O)을
 * 트랜잭션에 넣지 않기 위해서다. 각 단계(티켓 발급, 토큰 발급)는 자기 트랜잭션 안에서 끝나고
 * 여러 단계를 한 원자 단위로 묶어야 할 이유가 없다.
 */
@Service
public class SocialLoginService {

    private final Map<ProviderType, SocialOAuthClient> clients;
    private final UserRepository userRepository;
    private final SocialSignupTicketService socialSignupTicketService;
    private final EmailAvailabilityService emailAvailabilityService;
    private final TokenIssuer tokenIssuer;

    public SocialLoginService(List<SocialOAuthClient> clients,
                              UserRepository userRepository,
                              SocialSignupTicketService socialSignupTicketService,
                              EmailAvailabilityService emailAvailabilityService,
                              TokenIssuer tokenIssuer) {
        this.clients = clients.stream()
                .collect(Collectors.toMap(SocialOAuthClient::provider, Function.identity()));
        this.userRepository = userRepository;
        this.socialSignupTicketService = socialSignupTicketService;
        this.emailAvailabilityService = emailAvailabilityService;
        this.tokenIssuer = tokenIssuer;
    }

    public SocialLoginResponse login(ProviderType provider, String code) {
        SocialOAuthClient client = clients.get(provider);
        if (client == null) {
            throw new ApiException(AuthErrorCode.UNSUPPORTED_SOCIAL_PROVIDER);
        }
        SocialIdentity identity = client.exchange(code); // 외부 I/O — 트랜잭션 밖

        User existing = userRepository
                .findByProviderTypeAndProviderId(identity.provider(), identity.providerId())
                .orElse(null);
        if (existing != null) {
            // 로그인과 동일한 정책 — 탈퇴 유예 중에는 어떤 경로로도 세션을 새로 열 수 없다.
            if (existing.isWithdrawn()) {
                throw new ApiException(AuthErrorCode.WITHDRAWN_ACCOUNT);
            }
            return SocialLoginResponse.login(tokenIssuer.issue(existing));
        }

        // 같은 이메일을 다른 수단으로 이미 쓰고 있으면 여기서 끊는다.
        // User의 password XOR provider 제약 때문에 한 계정이 두 수단을 가질 수 없어 자동 연결은 불가능하다.
        emailAvailabilityService.assertAvailableForSignup(identity.email());

        String ticket = socialSignupTicketService.issue(identity);
        return SocialLoginResponse.signupRequired(ticket, identity.email());
    }
}
