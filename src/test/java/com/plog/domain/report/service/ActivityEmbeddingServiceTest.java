package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.report.repository.projection.EmbeddingClaimProjection;
import com.plog.infrastructure.ai.embedding.EmbeddingClient;
import com.plog.infrastructure.ai.embedding.EmbeddingGenerationException;
import com.plog.infrastructure.ai.embedding.EmbeddingRateLimitException;
import com.plog.infrastructure.ai.embedding.EmbeddingResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * TransactionTemplate이 실제 트랜잭션 매니저 없이도 콜백을 그대로 실행하도록,
 * PlatformTransactionManager를 최소한으로 흉내 낸 스텁을 쓴다 — 진짜 DB 트랜잭션 여부보다
 * "짧은 트랜잭션 경계로 쪼개졌다"는 서비스 로직 자체를 검증하는 게 이 테스트의 목적이다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityEmbeddingServiceTest {

    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private ChatMessageRepository chatMessageRepository;

    private ActivityEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new ActivityEmbeddingService(
                activityLogRepository, chatMessageRepository,
                embeddingClient, new ObjectMapper(), noopTransactionManager());
    }

    @Test
    void 채팅_원문은_로그가_아니라_원본_엔티티에서_처리중에만_읽는다() {
        EmbeddingClaimProjection claim = new EmbeddingClaimProjection() {
            public Long getId() { return 1L; }
            public String getContent() { return null; }
            public String getSourceDomain() { return SourceDomain.CHAT.name(); }
            public String getSourceRefId() { return "chat:15"; }
        };
        stubClaim(List.of(claim));
        ChatMessage message = org.mockito.Mockito.mock(ChatMessage.class);
        when(message.getMessage()).thenReturn("업무 관련 의견입니다");
        when(chatMessageRepository.findById(15L)).thenReturn(Optional.of(message));
        when(embeddingClient.embed("업무 관련 의견입니다"))
                .thenReturn(new EmbeddingResponse(List.of(0.2f), "gemini-embedding-001"));
        ReportActivityLog log = refinedChatLog(null);
        when(activityLogRepository.findById(1L)).thenReturn(Optional.of(log));
        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(log.getContent()).isNull();
        assertThat(log.hasEmbedding()).isTrue();
    }

    private static PlatformTransactionManager noopTransactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        return manager;
    }

    private EmbeddingClaimProjection claim(Long id, String content) {
        return new EmbeddingClaimProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getContent() {
                return content;
            }

            @Override
            public String getSourceDomain() {
                return null;
            }

            @Override
            public String getSourceRefId() {
                return null;
            }
        };
    }

    private ReportActivityLog refinedChatLog(String content) {
        ReportActivityLog log = ReportActivityLog.create(
                null, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, content, LocalDateTime.now(), null, null);
        log.applyNoiseFilter(false);
        return log;
    }

    private void stubClaim(List<EmbeddingClaimProjection> claimed) {
        when(activityLogRepository.selectClaimableEmbeddingActivities(any(), ArgumentMatchers.anyInt()))
                .thenReturn(claimed);
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        stubClaim(List.of());

        int embedded = service.embedBatch();

        assertThat(embedded).isZero();
        verify(activityLogRepository, never()).leaseForEmbedding(anyList(), any(), anyString());
    }

    @Test
    void 선점한_행에는_미래_시각과_토큰으로_리스를_찍는다() {
        stubClaim(List.of(claim(1L, "업무 관련 문장입니다")));
        when(embeddingClient.embed("업무 관련 문장입니다"))
                .thenReturn(new EmbeddingResponse(List.of(0.1f), "gemini-embedding-001"));
        ReportActivityLog log = refinedChatLog("업무 관련 문장입니다");
        when(activityLogRepository.findById(1L)).thenReturn(Optional.of(log));
        LocalDateTime beforeCall = LocalDateTime.now();

        service.embedBatch();

        ArgumentCaptor<LocalDateTime> leaseUntilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(activityLogRepository).leaseForEmbedding(
                ArgumentMatchers.eq(List.of(1L)), leaseUntilCaptor.capture(), tokenCaptor.capture());
        assertThat(leaseUntilCaptor.getValue()).isAfter(beforeCall);
        assertThat(tokenCaptor.getValue()).isNotBlank();
    }

    @Test
    void 정제된_텍스트로_임베딩을_생성해_저장한다() throws Exception {
        stubClaim(List.of(claim(1L, "업무 관련 문장입니다")));
        when(embeddingClient.embed("업무 관련 문장입니다"))
                .thenReturn(new EmbeddingResponse(List.of(0.1f, 0.2f, 0.3f), "gemini-embedding-001"));
        ReportActivityLog log = refinedChatLog("업무 관련 문장입니다");
        when(activityLogRepository.findById(1L)).thenReturn(Optional.of(log));

        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(log.getEmbeddingModel()).isEqualTo("gemini-embedding-001");
        assertThat(log.hasEmbedding()).isTrue();
        List<Float> savedVector = new ObjectMapper().readValue(
                log.getEmbedding(), new TypeReference<List<Float>>() {});
        assertThat(savedVector).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void 임베딩할_텍스트가_없는_행은_처리완료로만_표시하고_카운트에_넣지_않는다() {
        stubClaim(List.of(claim(1L, null)));
        ReportActivityLog statusChange = ReportActivityLog.create(
                null, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.now(), null, null);
        statusChange.applyNoiseFilter(false);
        when(activityLogRepository.findById(1L)).thenReturn(Optional.of(statusChange));

        int embedded = service.embedBatch();

        assertThat(embedded).isZero();
        assertThat(statusChange.getEmbeddingModel()).isEqualTo("N/A");
        assertThat(statusChange.hasEmbedding()).isFalse();
    }

    @Test
    void 임베딩_클라이언트_호출이_실패해도_배치_전체는_계속_진행한다() {
        stubClaim(List.of(claim(1L, "실패할 메시지"), claim(2L, "성공할 메시지")));
        when(embeddingClient.embed("실패할 메시지"))
                .thenThrow(new EmbeddingGenerationException("타임아웃"));
        when(embeddingClient.embed("성공할 메시지"))
                .thenReturn(new EmbeddingResponse(List.of(0.9f), "gemini-embedding-001"));
        ReportActivityLog succeeding = refinedChatLog("성공할 메시지");
        when(activityLogRepository.findById(2L)).thenReturn(Optional.of(succeeding));

        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(succeeding.getEmbeddingModel()).isEqualTo("gemini-embedding-001");
        verify(activityLogRepository, never()).findById(1L); // 실패한 건 저장 자체를 시도 안 함
    }

    @Test
    void 호출_한도에_걸리면_그_지점에서_배치를_멈추고_이후_건은_건드리지_않는다() {
        stubClaim(List.of(
                claim(1L, "한도 걸리기 전 메시지"),
                claim(2L, "한도에 걸리는 메시지"),
                claim(3L, "한도 걸린 후 메시지 — 시도조차 안 돼야 함")));
        when(embeddingClient.embed("한도 걸리기 전 메시지"))
                .thenReturn(new EmbeddingResponse(List.of(0.1f), "gemini-embedding-001"));
        when(embeddingClient.embed("한도에 걸리는 메시지"))
                .thenThrow(new EmbeddingRateLimitException("한도 초과", null));
        ReportActivityLog beforeLimit = refinedChatLog("한도 걸리기 전 메시지");
        when(activityLogRepository.findById(1L)).thenReturn(Optional.of(beforeLimit));

        int embedded = service.embedBatch();

        assertThat(embedded).isEqualTo(1);
        assertThat(beforeLimit.getEmbeddingModel()).isEqualTo("gemini-embedding-001");
        verify(embeddingClient, never()).embed("한도 걸린 후 메시지 — 시도조차 안 돼야 함");
        verify(activityLogRepository, never()).findById(3L);
    }

    @Test
    void 호출_한도에_걸리면_현재_행과_이후_미처리_행의_리스를_즉시_해제한다() {
        stubClaim(List.of(
                claim(1L, "한도 걸리기 전 메시지"),
                claim(2L, "한도에 걸리는 메시지"),
                claim(3L, "아직 처리 안 된 메시지")));
        when(embeddingClient.embed("한도 걸리기 전 메시지"))
                .thenReturn(new EmbeddingResponse(List.of(0.1f), "gemini-embedding-001"));
        when(embeddingClient.embed("한도에 걸리는 메시지"))
                .thenThrow(new EmbeddingRateLimitException("한도 초과", null));
        ReportActivityLog beforeLimit = refinedChatLog("한도 걸리기 전 메시지");
        when(activityLogRepository.findById(1L)).thenReturn(Optional.of(beforeLimit));

        service.embedBatch();

        ArgumentCaptor<String> leaseTokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> releaseTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(activityLogRepository).leaseForEmbedding(
                ArgumentMatchers.eq(List.of(1L, 2L, 3L)), any(), leaseTokenCaptor.capture());
        // 2번(한도 걸린 행)과 3번(아직 처리 안 된 행)의 리스가 풀린다. 1번(이미 완료)은 포함 안 됨.
        verify(activityLogRepository).releaseEmbeddingLease(
                ArgumentMatchers.eq(List.of(2L, 3L)), releaseTokenCaptor.capture());
        assertThat(releaseTokenCaptor.getValue()).isEqualTo(leaseTokenCaptor.getValue());
    }

    @Test
    void 배치_도중_일정_건수마다_남은_행의_리스를_갱신한다() {
        // LEASE_RENEWAL_INTERVAL(25)을 넘기도록 26건을 만든다 — i=25에서 갱신 1회 트리거.
        List<EmbeddingClaimProjection> claimed = new ArrayList<>();
        for (long id = 1; id <= 26; id++) {
            String content = "메시지 " + id;
            claimed.add(claim(id, content));
            when(embeddingClient.embed(content))
                    .thenReturn(new EmbeddingResponse(List.of(0.1f), "gemini-embedding-001"));
            when(activityLogRepository.findById(id))
                    .thenReturn(Optional.of(refinedChatLog(content)));
        }
        stubClaim(claimed);

        service.embedBatch();

        List<Long> remainingFromIndex25 = claimed.subList(25, 26).stream()
                .map(EmbeddingClaimProjection::getId)
                .toList();
        verify(activityLogRepository, times(1))
                .renewEmbeddingLease(ArgumentMatchers.eq(remainingFromIndex25), any(), any());
    }
}
