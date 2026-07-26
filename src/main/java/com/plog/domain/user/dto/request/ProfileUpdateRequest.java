package com.plog.domain.user.dto.request;

import com.plog.domain.user.entity.ProfilePreset;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(description = """
        프로필 수정 요청. 세 필드 모두 선택이며 **보낸 필드만 반영**됩니다.
        생략(null)한 필드는 기존 값이 유지됩니다.
        """)
public record ProfileUpdateRequest(
        // 필드가 선택(null 허용)이라 @NotBlank는 못 쓴다(null을 거부해버림).
        // @Pattern은 null을 통과시키는 기본 동작(JSR-380 컨벤션)을 그대로 이용해
        // "보내지 않음(null)"은 허용하되 "공백만 보냄"은 막는다 — 그래야 {"name":"  "} 같은 요청이
        // 실제 변경 없이도 1회뿐인 실명 변경 권리를 조용히 태워버리는 일을 막을 수 있다.
        @Schema(description = "변경할 실명. 계정당 1회만 변경 가능(USER001). 현재 값과 같으면 권리를 소모하지 않는다",
                example = "홍길동", nullable = true)
        @Pattern(regexp = ".*\\S.*", message = "실명은 공백일 수 없습니다.")
        String name,
        @Schema(description = "변경할 닉네임. 서버 전역에서 유일해야 한다(AUTH003)",
                example = "망고", nullable = true)
        @Pattern(regexp = ".*\\S.*", message = "닉네임은 공백일 수 없습니다.")
        String nickname,
        @Schema(description = "변경할 아바타 프리셋 8종 중 하나. null은 '변경하지 않음'이며 기본 아바타로 되돌리는 값이 아니다",
                example = "PENGUIN", nullable = true)
        ProfilePreset preset
) {
}
