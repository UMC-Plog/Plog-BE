package com.plog.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.RawActivityType;
import lombok.extern.slf4j.Slf4j;

/**
 * 2단계 분류의 규칙 기반 부분. 순수 함수 — 외부 상태 없음.
 * <p>
 * 두 가지 용도로 쓰인다:
 * <ol>
 *     <li>{@link #classifyContentless} — content가 없어(임베딩 자체가 없음) 애초에 벡터 비교 대상이
 *     아닌 유형(TASK_ATTACHMENT_ADD/TASK_STATUS_CHANGE)의 유일한 분류 경로.</li>
 *     <li>{@link #fallback} — content는 있지만 코사인 유사도가 임계값 미만이라 애매한 경우의
 *     rawActivityType 기반 기본값.</li>
 * </ol>
 * <p>
 * <b>가정(실측 없이 잡음)</b>: TASK_STATUS_CHANGE의 {@code metadata.newStatus} 매핑은 DONE(완료)을
 * 산출물이 완결됐다는 신호로 보아 DELIVERABLE_SUBMIT, 그 외(TODO/IN_PROGRESS) 전환은 업무 진행
 * 상태를 조정하는 행위로 보아 SCHEDULE_COORDINATION으로 둔다. 리포트 결과를 보고 이상하면 이
 * 메서드만 고치면 된다 — 호출부는 영향받지 않는다.
 */
@Slf4j
final class ActivityClassificationRules {

    private ActivityClassificationRules() {
    }

    /**
     * @throws IllegalArgumentException content가 있는(임베딩 비교 대상인) 유형이 들어온 경우 —
     *                                   호출부(배치 조회 조건)가 이미 걸러야 할 상황이라 방어적으로 막는다.
     */
    static ActivityCategory classifyContentless(
            RawActivityType rawActivityType, String metadataJson, ObjectMapper objectMapper
    ) {
        return switch (rawActivityType) {
            case TASK_ATTACHMENT_ADD -> ActivityCategory.DELIVERABLE_SUBMIT;
            case TASK_STATUS_CHANGE -> classifyStatusChange(metadataJson, objectMapper);
            default -> throw new IllegalArgumentException(
                    "content 없는 유형에 대한 규칙이 정의되지 않았습니다: " + rawActivityType);
        };
    }

    /**
     * @throws IllegalArgumentException 임베딩 분류 폴백 규칙이 정의되지 않은 유형이 들어온 경우
     */
    static ActivityCategory fallback(RawActivityType rawActivityType) {
        return switch (rawActivityType) {
            case POST_CREATE -> ActivityCategory.DELIVERABLE_SUBMIT;
            case COMMENT_CREATE -> ActivityCategory.FEEDBACK;
            case CHAT_MESSAGE -> ActivityCategory.SIMPLE_RESPONSE;
            default -> throw new IllegalArgumentException(
                    "임베딩 분류 폴백 규칙이 정의되지 않은 유형입니다: " + rawActivityType);
        };
    }

    private static ActivityCategory classifyStatusChange(String metadataJson, ObjectMapper objectMapper) {
        String newStatus = readNewStatus(metadataJson, objectMapper);
        if ("DONE".equals(newStatus)) {
            return ActivityCategory.DELIVERABLE_SUBMIT;
        }
        return ActivityCategory.SCHEDULE_COORDINATION;
    }

    private static String readNewStatus(String metadataJson, ObjectMapper objectMapper) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            JsonNode newStatusNode = node.get("newStatus");
            return newStatusNode == null || newStatusNode.isNull() ? null : newStatusNode.asText();
        } catch (Exception e) {
            log.warn("activity_status_metadata_parse_failed metadata={}", metadataJson, e);
            return null;
        }
    }
}