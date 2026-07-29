package com.plog.infrastructure.s3;

/**
 * 썸네일 S3 키 규칙. <b>백엔드가 소유한다.</b>
 * <p>
 * Lambda 에 같은 규칙을 두면 한쪽만 바뀌었을 때 조용히 어긋난다. Lambda 는 백엔드가
 * 계산해 payload 로 실어 준 targetKey 를 그대로 쓴다.
 * <p>
 * thumbs/ 접두는 IAM 권한 경계이자 S3 Lifecycle 규칙의 기준이다
 * (AttachmentUsage 가 예약해 둔 세그먼트).
 */
public final class ThumbnailKeys {

    private static final String PREFIX = "thumbs/";
    private static final String SUFFIX = ".webp";

    private ThumbnailKeys() {
    }

    /**
     * 확장자를 <b>치환하지 않고 덧붙인다.</b> 치환하면 photo.png 와 photo.PNG 가 같은
     * 썸네일 키로 충돌하고, 확장자 없는 파일에서 규칙이 깨진다.
     */
    public static String of(String sourceKey) {
        return PREFIX + sourceKey + SUFFIX;
    }
}
