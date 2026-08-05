package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.CollectionPhase;

/**
 * collector와 잡 워커 사이의 재개·진행 보고 통로.
 *
 * <p>GitHub collector만 실제로 사용한다. 나머지 provider는 리소스 하나가 단발 호출이라
 * 리소스 단위 커서로 충분하므로 이 컨텍스트를 무시한다.</p>
 */
interface CollectionContext {

    /** 재개 지점. 신규 수집이면 {@link CollectionCursor#start()}. */
    CollectionCursor cursor();

    /** 리소스 순회를 시작할 때 호출한다. 커서의 리소스 위치를 여기서 정한다. */
    void enterResource(Long resourceId);

    /** 항목 하나를 끝낼 때마다 호출한다. 커서 저장 주기는 구현체가 정한다. */
    void advance(CollectionPhase phase, int itemNumber);

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
        };
    }
}
