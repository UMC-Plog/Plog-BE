package com.plog.domain.integration.entity;

/** GitHub 리소스 하나를 수집하는 순서. 재개 커서가 ordinal 비교로 phase를 건너뛰므로 순서를 바꾸면 안 된다. */
public enum CollectionPhase {
    COMMITS,
    PULL_REQUESTS,
    ISSUES
}
