package com.plog.domain.user.service;

import com.plog.domain.user.dto.request.AgreementItem;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.entity.UserAgreement;
import com.plog.domain.user.repository.UserAgreementRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 약관 동의의 검증과 저장. 이메일 가입과 소셜 가입이 공유한다 —
 * 필수 약관 정의가 두 곳에 흩어지면 약관이 추가될 때 한쪽만 고치는 사고가 난다.
 */
@Service
public class UserAgreementService {

    // 가입 필수 약관 (MARKETING은 선택 — 철회 가능)
    private static final Set<AgreementType> REQUIRED_AGREEMENTS =
            EnumSet.of(AgreementType.SERVICE_TERMS, AgreementType.PRIVACY, AgreementType.EXTERNAL_DATA);

    private final UserAgreementRepository userAgreementRepository;

    public UserAgreementService(UserAgreementRepository userAgreementRepository) {
        this.userAgreementRepository = userAgreementRepository;
    }

    /** 중복 전송과 필수 누락을 걸러내고 약관 맵을 돌려준다. DB를 건드리기 전에 호출한다. */
    public Map<AgreementType, Boolean> validate(List<AgreementItem> items) {
        Map<AgreementType, Boolean> agreements = new EnumMap<>(AgreementType.class);
        for (AgreementItem item : items) {
            if (agreements.put(item.agreementType(), item.agreed()) != null) {
                throw new ApiException(ErrorCode.INVALID_INPUT); // 동일 약관 중복 전송
            }
        }
        boolean allAgreed = REQUIRED_AGREEMENTS.stream()
                .allMatch(required -> Boolean.TRUE.equals(agreements.get(required)));
        if (!allAgreed) {
            throw new ApiException(AuthErrorCode.REQUIRED_AGREEMENT_MISSING);
        }
        return agreements;
    }

    public void saveAll(User user, Map<AgreementType, Boolean> agreements) {
        List<UserAgreement> userAgreements = agreements.entrySet().stream()
                .map(entry -> UserAgreement.create(user, entry.getKey(), entry.getValue()))
                .toList();
        userAgreementRepository.saveAll(userAgreements);
    }
}
