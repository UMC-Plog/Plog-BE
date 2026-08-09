package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.RawActivityType;
import org.junit.jupiter.api.Test;

class ActivityClassificationRulesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 업무카드_첨부는_산출물제출로_분류한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_ATTACHMENT_ADD, null, objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 상태변경_DONE은_산출물제출로_분류한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_STATUS_CHANGE, "{\"newStatus\":\"DONE\"}", objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 상태변경_IN_PROGRESS는_일정조율로_분류한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_STATUS_CHANGE, "{\"newStatus\":\"IN_PROGRESS\"}", objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.SCHEDULE_COORDINATION);
    }

    @Test
    void 상태변경_TODO는_일정조율로_분류한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_STATUS_CHANGE, "{\"newStatus\":\"TODO\"}", objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.SCHEDULE_COORDINATION);
    }

    @Test
    void 상태변경_metadata가_없으면_일정조율로_폴백한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_STATUS_CHANGE, null, objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.SCHEDULE_COORDINATION);
    }

    @Test
    void 상태변경_metadata가_깨진_JSON이면_예외_없이_일정조율로_폴백한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_STATUS_CHANGE, "{broken json", objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.SCHEDULE_COORDINATION);
    }

    @Test
    void 상태변경_newStatus_필드가_없으면_일정조율로_폴백한다() {
        ActivityCategory result = ActivityClassificationRules.classifyContentless(
                RawActivityType.TASK_STATUS_CHANGE, "{\"taskId\":1}", objectMapper);

        assertThat(result).isEqualTo(ActivityCategory.SCHEDULE_COORDINATION);
    }

    @Test
    void content가_있는_유형은_규칙_기반_분류_대상이_아니다() {
        assertThatThrownBy(() -> ActivityClassificationRules.classifyContentless(
                RawActivityType.CHAT_MESSAGE, null, objectMapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 폴백_게시글_작성은_산출물제출이다() {
        assertThat(ActivityClassificationRules.fallback(RawActivityType.POST_CREATE))
                .isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 폴백_댓글은_피드백이다() {
        assertThat(ActivityClassificationRules.fallback(RawActivityType.COMMENT_CREATE))
                .isEqualTo(ActivityCategory.FEEDBACK);
    }

    @Test
    void 폴백_채팅은_단순응답이다() {
        assertThat(ActivityClassificationRules.fallback(RawActivityType.CHAT_MESSAGE))
                .isEqualTo(ActivityCategory.SIMPLE_RESPONSE);
    }

    @Test
    void 폴백_규칙이_없는_유형은_예외() {
        assertThatThrownBy(() -> ActivityClassificationRules.fallback(RawActivityType.GITHUB_COMMIT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}