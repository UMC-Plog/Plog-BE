package com.plog.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 토큰/인증코드 저장용 SHA-256 해시(hex). 비밀번호가 아니라 "조회 가능한 해시"가 필요한 값에 사용한다.
 * (비밀번호는 BCrypt — 여기 아님)
 */
public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 보장 → 사실상 도달 불가
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
