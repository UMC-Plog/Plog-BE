package com.plog.infrastructure.s3;

import java.util.Locale;

/** 업로드 객체의 수명 상태. S3 오브젝트 태그 {@code state} 값과 1:1 대응한다. */
public enum UploadedFileStatus {
    PENDING,
    CONFIRMED,
    ORPHANED;

    /** S3 태그에 들어가는 소문자 값. Lifecycle 규칙이 이 값을 필터한다. */
    public String tagValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
