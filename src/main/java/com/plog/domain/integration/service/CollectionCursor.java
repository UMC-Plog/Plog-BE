package com.plog.domain.integration.service;

import com.plog.domain.integration.entity.CollectionPhase;

/**
 * 수집 재개 지점.
 *
 * <p>{@code resourceId}가 진행 중이던 리소스이고, {@code phase}/{@code itemNumber}가 그 안의 위치다.
 * 리소스는 id 오름차순으로 순회하므로 이보다 작은 id는 건너뛰고, 같은 id는 phase부터 재개하며,
 * 큰 id는 처음부터 수집한다.</p>
 */
record CollectionCursor(Long resourceId, CollectionPhase phase, Integer itemNumber) {

    static CollectionCursor start() {
        return new CollectionCursor(null, null, null);
    }

    /** 이 리소스를 통째로 건너뛸 수 있는가. */
    boolean skipsResource(Long candidateResourceId) {
        return resourceId != null && candidateResourceId < resourceId;
    }

    /** 이 리소스를 커서 위치부터 재개해야 하는가. */
    boolean resumesResource(Long candidateResourceId) {
        return resourceId != null && candidateResourceId.equals(resourceId);
    }

    /** 재개 대상 리소스 안에서 이 phase를 건너뛸 수 있는가. */
    boolean skipsPhase(CollectionPhase candidate) {
        return phase != null && candidate.ordinal() < phase.ordinal();
    }

    /** 재개 대상 phase 안에서 이 항목을 건너뛸 수 있는가. */
    boolean skipsItem(CollectionPhase candidate, int candidateItemNumber) {
        return phase == candidate && itemNumber != null && candidateItemNumber <= itemNumber;
    }
}
