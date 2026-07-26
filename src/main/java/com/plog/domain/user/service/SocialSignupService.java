package com.plog.domain.user.service;

import com.plog.domain.user.dto.request.SocialSignupRequest;
import com.plog.domain.user.dto.response.TokenResponse;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.SocialSignupTicket;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 가입 완료. DB I/O만 하므로 메서드 레벨 @Transactional 로 묶는다(원자적: 티켓 소비 + User + 약관).
 * <p>
 * 실패 시 티켓 소비가 함께 롤백되는 것은 의도한 동작이다 — 닉네임 중복 같은 이유로 실패한 사용자가
 * 소셜 인증부터 다시 하지 않고 닉네임만 고쳐 재시도할 수 있어야 한다.
 * 동시 요청은 조건부 소비(consume 0행)와 uk_user_provider 유니크 제약이 막는다.
 */
@Service
public class SocialSignupService {

    private final UserRepository userRepository;
    private final SocialSignupTicketService socialSignupTicketService;
    private final UserAgreementService userAgreementService;
    private final TokenIssuer tokenIssuer;

    public SocialSignupService(UserRepository userRepository,
                               SocialSignupTicketService socialSignupTicketService,
                               UserAgreementService userAgreementService,
                               TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.socialSignupTicketService = socialSignupTicketService;
        this.userAgreementService = userAgreementService;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public TokenResponse signup(SocialSignupRequest request) {
        // 약관 검증을 먼저 한다 — 형식 오류로 티켓을 태우지 않기 위해.
        Map<AgreementType, Boolean> agreements = userAgreementService.validate(request.agreements());

        SocialSignupTicket ticket = socialSignupTicketService.consumeOrThrow(request.ticket());

        if (userRepository.existsByNickname(request.nickname())) {
            throw new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
        }

        // 이메일 중복은 여기서 다시 검사하지 않는다. 티켓 발급 시점에 이미 걸렀고,
        // 그 사이에 선점된 경우는 uk_user_email 유니크 제약이 최종 방어선이다.
        User user = User.createSocial(
                ticket.getEmail(),
                request.name(),
                request.nickname(),
                ticket.getProviderType(),
                ticket.getProviderId(),
                request.profilePreset());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolationMapper.map(e);
        }

        userAgreementService.saveAll(user, agreements);
        return tokenIssuer.issue(user);
    }
}
