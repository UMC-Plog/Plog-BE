package com.plog.infrastructure.s3;

import java.util.Locale;

/**
 * 첨부파일이 쓰이는 도메인. S3 키 접두사와 다운로드 동작을 함께 결정한다.
 * <p>
 * S3 이벤트 알림이 필터할 수 있는 것은 객체 키뿐이라, 나중에 도메인별 후처리
 * (예: 채팅 이미지만 썸네일 생성)를 걸려면 키에 용도가 남아 있어야 한다.
 * <p>
 * 다운로드 정책을 여기 두면 호출부가 오버로드를 고를 필요가 없어, 새 도메인을
 * 붙이는 사람이 잘못된 동작을 고를 여지가 사라진다.
 */
public enum AttachmentUsage {
    /** 채팅은 이미지를 대화 안에서 그대로 보여준다. */
    CHAT(false),
    POST(true),
    TASK(true);

    private final boolean forceDownload;

    AttachmentUsage(boolean forceDownload) {
        this.forceDownload = forceDownload;
    }

    /** 참이면 응답에 Content-Disposition: attachment 를 붙여 내려받게 한다. */
    public boolean forcesDownload() {
        return forceDownload;
    }

    /**
     * S3 키의 최상위 세그먼트. 도메인별 IAM 권한 경계와 Lifecycle 세분화의 기준이다.
     * <p>
     * 상태(pending/confirmed/orphaned)는 키가 아니라 오브젝트 태그가 들고 있어
     * 키는 생성 후 절대 이동하지 않는다. thumbs/ 는 향후 썸네일용으로 예약한다.
     */
    public String keySegment() {
        return name().toLowerCase(Locale.ROOT) + "s";
    }
}
