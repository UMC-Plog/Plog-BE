package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.plog.domain.user.dto.request.AgreementItem;
import com.plog.domain.user.entity.AgreementType;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.entity.UserAgreement;
import com.plog.domain.user.repository.UserAgreementRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserAgreementServiceTest {

    private UserAgreementRepository userAgreementRepository;
    private UserAgreementService service;

    @BeforeEach
    void setUp() {
        userAgreementRepository = mock(UserAgreementRepository.class);
        service = new UserAgreementService(userAgreementRepository);
    }

    private static AgreementItem item(AgreementType type, boolean agreed) {
        return new AgreementItem(type, agreed);
    }

    @Test
    @DisplayName("필수 3종에 모두 동의하면 통과한다")
    void acceptsAllRequired() {
        Map<AgreementType, Boolean> result = service.validate(List.of(
                item(AgreementType.SERVICE_TERMS, true),
                item(AgreementType.PRIVACY, true),
                item(AgreementType.EXTERNAL_DATA, true),
                item(AgreementType.MARKETING, false)));

        assertThat(result).containsEntry(AgreementType.MARKETING, false);
        assertThat(result).hasSize(4);
    }

    @Test
    @DisplayName("필수 약관이 하나라도 빠지면 REQUIRED_AGREEMENT_MISSING")
    void rejectsMissingRequired() {
        assertThatThrownBy(() -> service.validate(List.of(
                item(AgreementType.SERVICE_TERMS, true),
                item(AgreementType.PRIVACY, true))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REQUIRED_AGREEMENT_MISSING);
    }

    @Test
    @DisplayName("필수 약관에 false로 동의하면 REQUIRED_AGREEMENT_MISSING")
    void rejectsDeclinedRequired() {
        assertThatThrownBy(() -> service.validate(List.of(
                item(AgreementType.SERVICE_TERMS, true),
                item(AgreementType.PRIVACY, true),
                item(AgreementType.EXTERNAL_DATA, false))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.REQUIRED_AGREEMENT_MISSING);
    }

    @Test
    @DisplayName("같은 약관을 중복 전송하면 INVALID_INPUT")
    void rejectsDuplicateAgreement() {
        assertThatThrownBy(() -> service.validate(List.of(
                item(AgreementType.SERVICE_TERMS, true),
                item(AgreementType.SERVICE_TERMS, false))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("동의 목록을 유저에 연결해 저장한다")
    void savesAgreements() {
        User user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);

        service.saveAll(user, Map.of(AgreementType.SERVICE_TERMS, true, AgreementType.MARKETING, false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserAgreement>> captor = ArgumentCaptor.forClass(List.class);
        verify(userAgreementRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }
}
