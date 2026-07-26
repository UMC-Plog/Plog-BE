package com.plog.domain.user.service;

import com.plog.domain.user.dto.request.ProfileUpdateRequest;
import com.plog.domain.user.dto.response.ProfileResponse;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.UserErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 조회/수정. 인증된 유저가 마이페이지에서 프로필(실명/닉네임/아바타 프리셋)을 확인하고 바꾼다.
 * 조회·중복확인은 로드한 엔티티를 읽기만 하므로 별도 save가 필요 없지만,
 * updateProfile은 닉네임 유니크 위반을 즉시 표면화하기 위해 saveAndFlush를 명시적으로 호출한다.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return ProfileResponse.from(requireUser(userId));
    }

    /**
     * 마이페이지용 닉네임 중복확인. 가입용(UserService.checkNicknameAvailable)과 딱 하나 다르다 —
     * 본인의 현재 닉네임은 사용 가능으로 통과시킨다. 가입용은 공개 API라 본인을 판별할 수 없어,
     * 그걸 마이페이지에서 쓰면 자기 닉네임에 대해 중복 에러가 난다.
     */
    @Transactional(readOnly = true)
    public void checkNicknameAvailable(Long userId, String nickname) {
        User user = requireUser(userId);
        if (nickname.equals(user.getNickname())) {
            return;
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
        }
    }

    /**
     * 프로필 부분 수정. 보낸 필드만 반영한다.
     * 한 트랜잭션으로 묶어 닉네임 중복 실패 시 프리셋만 반영되는 부분 성공을 만들지 않는다.
     */
    @Transactional
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = requireUser(userId);

        if (request.name() != null && !request.name().equals(user.getName())) {
            if (!user.isNameChangeAvailable()) {
                throw new ApiException(UserErrorCode.NAME_CHANGE_ALREADY_USED);
            }
            user.changeName(request.name(), TimeUtil.nowUtc());
        }
        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new ApiException(AuthErrorCode.NICKNAME_DUPLICATED);
            }
            user.changeNickname(request.nickname());
        }
        if (request.preset() != null) {
            user.changeProfilePreset(request.preset());
        }

        try {
            // flush로 유니크 위반을 지금 표면화 → 확인~저장 사이 선점(TOCTOU)을 에러코드로 변환
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw UniqueViolationMapper.map(e);
        }
    }

    /**
     * 프로필 3개 엔드포인트(조회/중복확인/수정)의 공통 진입 검사.
     * 탈퇴 계정을 여기서 막는다 — 탈퇴 후에도 액세스 토큰이 최대 30분 살아있는 건 스펙상 허용이지만,
     * 그게 "쓰기 창"이 되면 죽은 계정이 닉네임을 7일간 선점하거나, 1회뿐인 실명 변경 권리를
     * 파기 배치가 덮어쓸 행에 소모한다. 조회도 함께 막는다 — 이미 죽은 계정의 개인정보를 계속 내줄 이유가 없다.
     * 로그인·비밀번호 재설정·재가입·탈퇴가 모두 같은 코드로 거부하므로 계정 표면 전체가 일관된다.
     */
    private User requireUser(Long userId) {
        // 유효 토큰이 존재하지 않는 유저를 가리키면 토큰 무효로 취급(기존 컨벤션).
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_TOKEN));
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        return user;
    }
}
