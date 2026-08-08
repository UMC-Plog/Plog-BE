package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import java.util.List;

import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingGenerationException;
import com.plog.infrastructure.ai.embedding.EmbeddingRateLimitException;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class ActivityEmbeddingServiceTest {

    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private EmbeddingClient embeddingClient;

    private ActivityEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new ActivityEmbeddingService(activityLogRepository, embeddingClient, new ObjectMapper());
    }

    private ReportActivityLog refinedChatLog(String content) {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, content, LocalDateTime.now(), null, null);
        log.applyNoiseFilter(false);
        return log;
    }

    private void stubFetch(List<ReportActivityLog> logs) {
        when(activityLogRepository.findByNoiseFilteredFalseAndEmbeddingModelIsNullOrderByOccurredAtAscIdAsc(
                ArgumentMatchers.any(Limit.class)))
                .thenReturn(logs);
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        stubFetch(List.of());

        int embedded = service.embedBatch();

        assertThat(embedded).isZero();
    }

    @Test
    void 정제된_텍스트로_임베딩을_생성해_저장한다() throws Exception {
        ReportActivityLog log = refinedChatLog("업무 관련 문장입니다");
        stubFetch(List.of(log));
        when(embeddingClient.embed("업무 관련 문장입니다"))
                .thenReturn(new EmbeddingResponse(List.of(0.1f, 0.2f, 0.3f), "bge-m3"));

        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(log.getEmbeddingModel()).isEqualTo("bge-m3");
        assertThat(log.hasEmbedding()).isTrue();
        List<Float> savedVector = new ObjectMapper().readValue(
                log.getEmbedding(), new TypeReference<List<Float>>() {});
        assertThat(savedVector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void 임베딩할_텍스트가_없는_행은_처리완료로만_표시하고_카운트에_넣지_않는다() {
        ReportActivityLog statusChange = ReportActivityLog.create(
                null, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.now(), null, null);
        statusChange.applyNoiseFilter(false);
        stubFetch(List.of(statusChange));

        int embedded = service.embedBatch();

        assertThat(embedded).isZero();
        assertThat(statusChange.getEmbeddingModel()).isEqualTo("N/A");
        assertThat(statusChange.hasEmbedding()).isFalse();
    }

    @Test
    void 단독_감탄사만_있어_정제_후_비는_행도_처리완료로만_표시한다() {
        ReportActivityLog log = refinedChatLog("음"); // ActivityContentRefiner가 노이즈로 판정하는 단독 감탄사
        stubFetch(List.of(log));

        int embedded = service.embedBatch();

        assertThat(embedded).isZero();
        assertThat(log.getEmbeddingModel()).isEqualTo("N/A");
    }

    @Test
    void 임베딩_클라이언트_호출이_실패해도_배치_전체는_계속_진행한다() {
        ReportActivityLog failing = refinedChatLog("실패할 메시지");
        ReportActivityLog succeeding = refinedChatLog("성공할 메시지");
        stubFetch(List.of(failing, succeeding));
        when(embeddingClient.embed("실패할 메시지"))
                .thenThrow(new EmbeddingGenerationException("타임아웃"));
        when(embeddingClient.embed("성공할 메시지"))
                .thenReturn(new EmbeddingResponse(List.of(0.9f), "bge-m3"));

        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(failing.getEmbeddingModel()).isNull(); // 실패한 건 다음 배치에서 재시도되도록 null 유지
        assertThat(succeeding.getEmbeddingModel()).isEqualTo("bge-m3");
    }

    @Test
    void 호출_한도에_걸리면_그_지점에서_배치를_멈추고_이후_건은_건드리지_않는다() {
        ReportActivityLog beforeLimit = refinedChatLog("한도 걸리기 전 메시지");
        ReportActivityLog hitsLimit = refinedChatLog("한도에 걸리는 메시지");
        ReportActivityLog afterLimit = refinedChatLog("한도 걸린 후 메시지 — 시도조차 안 돼야 함");
        stubFetch(List.of(beforeLimit, hitsLimit, afterLimit));
        when(embeddingClient.embed("한도 걸리기 전 메시지"))
                .thenReturn(new EmbeddingResponse(List.of(0.1f), "gemini-embedding-001"));
        when(embeddingClient.embed("한도에 걸리는 메시지"))
                .thenThrow(new EmbeddingRateLimitException("한도 초과", null));

        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(beforeLimit.getEmbeddingModel()).isEqualTo("gemini-embedding-001");
        assertThat(hitsLimit.getEmbeddingModel()).isNull(); // 다음 배치에서 재시도
        assertThat(afterLimit.getEmbeddingModel()).isNull(); // 아예 시도되지 않음 (embed 호출 안 됨)
        org.mockito.Mockito.verify(embeddingClient, org.mockito.Mockito.never())
                .embed("한도 걸린 후 메시지 — 시도조차 안 돼야 함");
    }
}