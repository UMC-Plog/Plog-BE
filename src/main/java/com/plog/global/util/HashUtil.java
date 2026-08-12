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

    private static final int FINGERPRINT_LENGTH = 12;

    private HashUtil() {
    }

    /**
     * 로그용 짧은 지문. 원문도, 조회에 쓰는 전체 해시도 남기지 않으면서
     * "같은 토큰으로 들어온 요청인지"만 로그끼리 대조할 수 있게 한다.
     * 로그가 유출돼도 이 값으로는 토큰을 되돌릴 수 없다.
     */
    public static String fingerprint(String raw) {
        return sha256Hex(raw).substring(0, FINGERPRINT_LENGTH);
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
