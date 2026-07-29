package com.plog.infrastructure.s3;

/**
 * 썸네일 생성 상태.
 * <p>
 * thumbnail_key 하나만 두고 null 로 판단하면 "대상이 아님(pdf)" · "아직 안 만들어짐" ·
 * "만들다 실패함" 셋이 뭉개진다. 스케줄러가 매 틱 전 CONFIRMED 행을 훑게 되고,
 * 실패한 행이 영원히 재시도된다.
 */
public enum ThumbnailStatus {
    /** 대상 아님(비이미지 또는 CHAT 외 도메인). 영구. */
    NONE,
    /** 생성 대상. 스케줄러의 작업 큐. */
    PENDING,
    /** thumbnail_key 에 객체가 있음을 확인함. */
    READY,
    /** 재시도 상한 초과. 영구. 프론트는 원본을 표시한다. */
    FAILED
}
