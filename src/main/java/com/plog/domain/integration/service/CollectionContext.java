package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.CollectionPhase;

/**
 * collector와 잡 워커 사이의 재개·진행 보고 통로.
 *
 * <p>GitHub collector는 재개 커서와 진행 보고를 사용하고, pagination이나 재귀 호출이 긴
 * 다른 provider collector는 heartbeat만 사용한다.</p>
 */
interface CollectionContext {

    /** 재개 지점. 신규 수집이면 {@link CollectionCursor#start()}. */
    CollectionCursor cursor();

    /** 리소스 순회를 시작할 때 호출한다. 커서의 리소스 위치를 여기서 정한다. */
    void enterResource(Long resourceId);

    /** 항목 하나를 끝낼 때마다 호출한다. 커서 저장 주기는 구현체가 정한다. */
    void advance(CollectionPhase phase, int itemNumber);

    /**
     * provider 호출 1건마다 호출한다. 커서는 건드리지 않고 살아있다는 신호만 남긴다.
     *
     * <p>commit 페이지네이션처럼 항목 경계 없이 수백 번 호출이 이어지는 구간이 있어서,
     * {@link #advance}만으로는 heartbeat 공백이 processing timeout을 넘길 수 있다.</p>
     */
    void heartbeat();

    /** 재개도 진행 보고도 필요 없는 호출자용. 테스트와 Notion webhook 경로가 쓴다. */
    static CollectionContext noop() {
        return new CollectionContext() {
            @Override
            public CollectionCursor cursor() {
                return CollectionCursor.start();
            }

            @Override
            public void enterResource(Long resourceId) {
                // 커서를 저장할 잡이 없다.
            }

            @Override
            public void advance(CollectionPhase phase, int itemNumber) {
                // 진행 보고를 받을 잡이 없다.
            }

            @Override
            public void heartbeat() {
                // 회수될 잡이 없다.
            }
        };
    }
}
