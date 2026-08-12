package com.plog.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashUtilTest {

    private static final String RAW_TOKEN = "3Qm7xk-secret-refresh-token-value_9Zb";

    @Test
    @DisplayName("지문은 토큰 원문을 담지 않는다")
    void fingerprintNeverLeaksRawToken() {
        String fingerprint = HashUtil.fingerprint(RAW_TOKEN);

        assertThat(fingerprint).doesNotContain(RAW_TOKEN);
        // 원문 조각도 남으면 안 된다 — 로그가 새면 그대로 토큰 복원 단서가 된다.
        assertThat(RAW_TOKEN).doesNotContain(fingerprint);
    }

    @Test
    @DisplayName("같은 원문은 같은 지문, 다른 원문은 다른 지문을 만든다")
    void fingerprintIsStableAndDistinguishing() {
        assertThat(HashUtil.fingerprint(RAW_TOKEN)).isEqualTo(HashUtil.fingerprint(RAW_TOKEN));
        assertThat(HashUtil.fingerprint(RAW_TOKEN)).isNotEqualTo(HashUtil.fingerprint(RAW_TOKEN + "x"));
    }

    @Test
    @DisplayName("지문은 전체 해시가 아니라 앞 12자리만 쓴다")
    void fingerprintIsShortenedHash() {
        String fingerprint = HashUtil.fingerprint(RAW_TOKEN);

        assertThat(fingerprint).hasSize(12);
        assertThat(HashUtil.sha256Hex(RAW_TOKEN)).startsWith(fingerprint);
    }
}
