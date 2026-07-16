package com.plog.domain.user.service;

import com.plog.domain.user.dto.request.SignupRequest;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.EmailVerification;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.entity.UserAgreement;
import com.plog.domain.user.repository.EmailVerificationRepository;
import com.plog.domain.user.repository.UserAgreementRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 및 닉네임 중복확인. 회원가입은 DB I/O만 → 메서드 레벨 @Transactional(원자적: User + 약관).
 */
@Service
public class UserService {

    // 가입 필수 약관 (MARKETING은 선택 — 철회 가능)
    private static final Set<AgreementType> REQUIRED_AGREEMENTS =
            EnumSet.of(AgreementType.SERVICE_TERMS, AgreementType.PRIVACY, AgreementType.EXTERNAL_DATA);

    private final UserRepository userRepository;
    private final UserAgreementRepository userAgreementRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserAgreementRepository userAgreementRepository,
                       EmailVerificationRepository emailVerificationRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userAgreementRepository = userAgreementRepository;
        this.emailVerificationRepository = emailVerificationRepository;
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
        Map<AgreementType, Boolean> agreements = toAgreementMap(request.agreements());
        validateRequiredAgreements(agreements);

        EmailVerification verification = getVerifiedOrThrow(request.email());
        assertEmailNotRegistered(request.email());
        if (userRepository.existsByNickname(request.nickname())) {
            throw new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
        }

        User user = User.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.nickname());
        try {
            // flush로 유니크 위반을 지금 표면화 → 확인~가입 사이 선점(TOCTOU)을 에러코드로 변환
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw mapUniqueViolation(e);
        }

        List<UserAgreement> userAgreements = agreements.entrySet().stream()
                .map(entry -> UserAgreement.create(user, entry.getKey(), entry.getValue()))
                .toList();
        userAgreementRepository.saveAll(userAgreements);

        emailVerificationRepository.delete(verification); // 인증 소비 — 재사용 방지
    }

    private Map<AgreementType, Boolean> toAgreementMap(List<SignupRequest.AgreementItem> items) {
        Map<AgreementType, Boolean> map = new EnumMap<>(AgreementType.class);
        for (SignupRequest.AgreementItem item : items) {
            if (map.put(item.agreementType(), item.agreed()) != null) {
                throw new ApiException(ErrorCode.INVALID_INPUT); // 동일 약관 중복 전송
            }
        }
        return map;
    }

    private void validateRequiredAgreements(Map<AgreementType, Boolean> agreements) {
        boolean allAgreed = REQUIRED_AGREEMENTS.stream()
                .allMatch(required -> Boolean.TRUE.equals(agreements.get(required)));
        if (!allAgreed) {
            throw new ApiException(AuthErrorCode.REQUIRED_AGREEMENT_MISSING);
        }
    }

    /** 인증 완료 + 가입 이메일 바인딩 확인. 인증한 이메일로만 가입 가능. */
    private EmailVerification getVerifiedOrThrow(String email) {
        EmailVerification verification = emailVerificationRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED));
        if (!verification.isVerified()) {
            throw new ApiException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
        return verification;
    }

    private void assertEmailNotRegistered(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return;
        }
        throw user.isSocialUser()
                ? new ApiException(AuthErrorCode.EMAIL_DUPLICATED_SOCIAL)
                : new ApiException(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
    }

    private ApiException mapUniqueViolation(DataIntegrityViolationException e) {
        String constraint = (e.getCause() instanceof ConstraintViolationException cve)
                ? cve.getConstraintName() : null;
        if (constraint != null) {
            if (constraint.equalsIgnoreCase("uk_user_nickname")) {
                return new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
            }
            if (constraint.equalsIgnoreCase("uk_user_email")) {
                return new ApiException(AuthErrorCode.EMAIL_DUPLICATED_LOCAL);
            }
        }
        return new ApiException(ErrorCode.CONFLICT);
    }
}
