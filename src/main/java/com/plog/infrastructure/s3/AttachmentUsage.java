package com.plog.infrastructure.s3;

import java.util.Locale;

/**
 * 첨부파일이 쓰이는 도메인. S3 키 접두사를 결정한다.
 * <p>
 * S3 이벤트 알림이 필터할 수 있는 것은 객체 키뿐이라, 나중에 도메인별 후처리
 * (예: 채팅 이미지만 썸네일 생성)를 걸려면 키에 용도가 남아 있어야 한다.
 */
public enum AttachmentUsage {
    CHAT,
    POST,
    TASK;

    /**
     * S3 키의 최상위 세그먼트. 도메인별 IAM 권한 경계와 Lifecycle 세분화의 기준이다.
     * <p>
     * 상태(pending/confirmed/orphaned)는 키가 아니라 오브젝트 태그가 들고 있어
     * 키는 생성 후 절대 이동하지 않는다. thumbs/ 는 향후 썸네일용으로 예약한다.
     * <p>
     * 다운로드 동작(인라인/내려받기) 판단은 더 이상 여기 없다. 채팅은 프록시 컨트롤러가
     * 헤더를 직접 붙이고, 게시글·업무카드는 발급 시점에 Content-Disposition 을 넣는다.
     * <b>API 경로를 이 값으로 조립하지 않는다</b> — S3 키 규칙을 바꾸면 URL 이 따라 바뀐다.
     */
    public String keySegment() {
        return name().toLowerCase(Locale.ROOT) + "s";
    }
}
