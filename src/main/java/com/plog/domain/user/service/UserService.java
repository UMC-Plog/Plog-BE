package com.plog.domain.user.service;

import com.plog.domain.user.dto.request.SignupRequest;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.EmailVerificationPurpose;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 및 닉네임 중복확인. 회원가입은 DB I/O만 → 메서드 레벨 @Transactional(원자적: User + 약관).
 * 약관 검증·저장과 이메일 가용성 판정은 소셜 가입과 공유하는 컴포넌트에 위임한다.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserAgreementService userAgreementService;
    private final EmailAvailabilityService emailAvailabilityService;
    private final EmailVerificationCodeService emailVerificationCodeService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserAgreementService userAgreementService,
                       EmailAvailabilityService emailAvailabilityService,
                       EmailVerificationCodeService emailVerificationCodeService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userAgreementService = userAgreementService;
        this.emailAvailabilityService = emailAvailabilityService;
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.passwordEncoder = passwordEncoder;
    }

    /** 닉네임 중복확인. 사용 중이면 예외 — 단, 최종 방어선은 가입 시 유니크 제약이다(TOCTOU). */
    @Transactional(readOnly = true)
    public void checkNicknameAvailable(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
        }
    }

    @Transactional
    public void signup(SignupRequest request) {
        Map<AgreementType, Boolean> agreements = userAgreementService.validate(request.agreements());

        EmailVerification verification = getVerifiedOrThrow(request.email());
        emailAvailabilityService.assertAvailableForSignup(request.email());
        if (userRepository.existsByNickname(request.nickname())) {
            throw new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
        }

        User user = User.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.nickname(),
                request.profilePreset());
        try {
            // flush로 유니크 위반을 지금 표면화 → 확인~가입 사이 선점(TOCTOU)을 에러코드로 변환
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolationMapper.map(e);
        }

        userAgreementService.saveAll(user, agreements);

        emailVerificationCodeService.consume(verification); // 인증 소비 — 재사용 방지
    }

    /** 인증 완료 + 가입 이메일 바인딩 확인. 인증한 이메일로만 가입 가능. */
    private EmailVerification getVerifiedOrThrow(String email) {
        return emailVerificationCodeService.requireVerified(email, EmailVerificationPurpose.SIGNUP);
    }
}
