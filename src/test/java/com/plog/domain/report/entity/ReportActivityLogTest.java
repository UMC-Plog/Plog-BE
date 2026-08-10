package com.plog.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.plog.domain.task.entity.Task;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ReportActivityLogTest {

    private ReportActivityLog refinedChatLog() {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "정제된 텍스트", LocalDateTime.of(2026, 8, 7, 10, 0), null, null);
        log.applyNoiseFilter(false);
        return log;
    }

    /** 테스트 전용: embeddingLeaseUntil/Token은 리포지토리 벌크 업데이트로만 채워져서 public setter가 없다. */
    private void assignLease(ReportActivityLog activity, LocalDateTime leaseUntil, String leaseToken) {
        try {
            Field untilField = ReportActivityLog.class.getDeclaredField("embeddingLeaseUntil");
            untilField.setAccessible(true);
            untilField.set(activity, leaseUntil);

            Field tokenField = ReportActivityLog.class.getDeclaredField("embeddingLeaseToken");
            tokenField.setAccessible(true);
            tokenField.set(activity, leaseToken);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 정제_전에는_임베딩을_적용할_수_없다() {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "아직 정제 전", LocalDateTime.now(), null, null);

        assertThatThrownBy(() -> log.applyEmbedding("bge-m3", "[0.1,0.2]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 노이즈로_판정된_행에는_임베딩을_적용할_수_없다() {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE,
                "노이즈", LocalDateTime.now(), null, null);
        log.applyNoiseFilter(true);

        assertThatThrownBy(() -> log.applyEmbedding("bge-m3", "[0.1,0.2]"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 정제를_통과한_행에_임베딩을_적용하면_모델명과_벡터가_저장된다() {
        ReportActivityLog log = refinedChatLog();

        log.applyEmbedding("bge-m3", "[0.1,0.2,0.3]");

        assertThat(log.getEmbeddingModel()).isEqualTo("bge-m3");
        assertThat(log.getEmbedding()).isEqualTo("[0.1,0.2,0.3]");
        assertThat(log.hasEmbedding()).isTrue();
    }

    @Test
    void 임베딩을_적용하면_걸려있던_리스와_토큰이_해제된다() {
        ReportActivityLog log = refinedChatLog();
        assignLease(log, LocalDateTime.now().plusMinutes(30), "lease-token-abc");

        log.applyEmbedding("bge-m3", "[0.1,0.2,0.3]");

        assertThat(log.getEmbeddingLeaseUntil()).isNull();
        assertThat(log.getEmbeddingLeaseToken()).isNull();
    }

    @Test
    void 처리완료로만_표시해도_걸려있던_리스와_토큰이_해제된다() {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.now(), null, null);
        log.applyNoiseFilter(false);
        assignLease(log, LocalDateTime.now().plusMinutes(30), "lease-token-abc");

        log.markEmbeddingNotApplicable();

        assertThat(log.getEmbeddingLeaseUntil()).isNull();
        assertThat(log.getEmbeddingLeaseToken()).isNull();
    }

    @Test
    void 모델명이나_벡터가_비어있으면_임베딩을_적용할_수_없다() {
        ReportActivityLog log = refinedChatLog();

        assertThatThrownBy(() -> log.applyEmbedding("", "[0.1]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> log.applyEmbedding("bge-m3", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 임베딩할_텍스트가_없는_행은_처리완료로만_표시하고_벡터는_비운다() {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.now(), null, null);
        log.applyNoiseFilter(false);

        log.markEmbeddingNotApplicable();

        assertThat(log.getEmbeddingModel()).isEqualTo("N/A");
        assertThat(log.getEmbedding()).isNull();
        assertThat(log.hasEmbedding()).isFalse();
    }

    @Test
    void linkedTask를_받는_오버로드는_생성_시점에_바로_연결한다() {
        Task task = Task.builder().id(1L).build();

        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.now(), null, null, task);

        // noiseFiltered가 아직 확정 전(null)이라 linkTask()는 IllegalStateException을 던지는 상태지만,
        // 생성 시점에 바로 채운 값이라 그 제약과 무관하게 이미 연결돼 있어야 한다.
        assertThat(log.getLinkedTask()).isSameAs(task);
        assertThat(log.getNoiseFiltered()).isNull();
    }

    @Test
    void linkedTask를_생략하면_null로_생성된다() {
        ReportActivityLog log = refinedChatLog();

        assertThat(log.getLinkedTask()).isNull();
    }
}