package com.plog.domain.task.event;

import java.time.LocalDateTime;

/**
 * 업무카드에 산출물(파일/링크)이 첨부됐을 때 발행된다. 카드 생성 시 동봉된 첨부와, 기존 카드에
 * 추가된 첨부 모두 이 이벤트를 쓴다. report 도메인의 0단계 수집({@code TaskActivityLogListener})이
 * 구독한다.
 */
public record TaskAttachmentAddedEvent(
        Long attachmentId,
        Long taskId,
        Long projectMemberId,
        LocalDateTime occurredAt
) {
}