package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.ActivityCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class ActivityClassificationServiceTest {

    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private ActivityAnchorCache anchorCache;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActivityClassificationService service;

    @BeforeEach
    void setUp() {
        service = new ActivityClassificationService(activityLogRepository, anchorCache, objectMapper);
    }

    private ProjectMember member() {
        return ProjectMember.builder().id(1L).build();
    }

    private void assignId(ReportActivityLog activity, Long id) {
        try {
            Field field = ReportActivityLog.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(activity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ReportActivityLog embeddedLog(
            RawActivityType rawType, SourceDomain domain, String content, List<Float> vector, Long id
    ) {
        ReportActivityLog log = ReportActivityLog.create(
                member(), domain, rawType, content, LocalDateTime.of(2026, 8, 7, 10, 0), null, null);
        assignId(log, id);
        log.applyNoiseFilter(false);
        if (vector != null) {
            log.applyEmbedding("gemini-embedding-001", writeJson(vector));
        }
        return log;
    }

    private ReportActivityLog contentlessLog(RawActivityType rawType, String metadata, Long id) {
        ReportActivityLog log = ReportActivityLog.create(
                member(), SourceDomain.TASK, rawType, null, LocalDateTime.of(2026, 8, 7, 10, 0), metadata, null);
        assignId(log, id);
        log.applyNoiseFilter(false);
        log.markEmbeddingNotApplicable();
        return log;
    }

    private ReportActivityLog embeddedLogWithRawJson(
            RawActivityType rawType, SourceDomain domain, String content, String embeddingJson, Long id
    ) {
        ReportActivityLog log = ReportActivityLog.create(
                member(), domain, rawType, content, LocalDateTime.of(2026, 8, 7, 10, 0), null, null);
        assignId(log, id);
        log.applyNoiseFilter(false);
        log.applyEmbedding("gemini-embedding-001", embeddingJson);
        return log;
    }

    private String writeJson(List<Float> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubFetch(List<ReportActivityLog> logs) {
        when(activityLogRepository.findClassificationTargets(any(), any(LocalDateTime.class), any(Limit.class)))
                .thenReturn(logs);
    }

    /** anchorCache가 모델 "gemini-embedding-001", FEEDBACK=[1,0], SIMPLE_RESPONSE=[0,1] centroid를 갖는다고 가정. */
    private void stubTwoCategoryAnchors() {
        when(anchorCache.modelName()).thenReturn("gemini-embedding-001");
        when(anchorCache.cachedCategories())
                .thenReturn(Set.of(ActivityCategory.FEEDBACK, ActivityCategory.SIMPLE_RESPONSE));
        when(anchorCache.centroidOf(ActivityCategory.FEEDBACK)).thenReturn(List.of(1.0f, 0.0f));
        when(anchorCache.centroidOf(ActivityCategory.SIMPLE_RESPONSE)).thenReturn(List.of(0.0f, 1.0f));
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        stubFetch(List.of());

        int classified = service.classifyBatch();

        assertThat(classified).isZero();
    }

    @Test
    void 배치_조회는_TASK_CHAT_POST_도메인만_대상으로_한다() {
        stubFetch(List.of());

        service.classifyBatch();

        ArgumentCaptor<List<SourceDomain>> domainsCaptor = ArgumentCaptor.forClass(List.class);
        verify(activityLogRepository)
                .findClassificationTargets(domainsCaptor.capture(), any(LocalDateTime.class), any(Limit.class));
        assertThat(domainsCaptor.getValue())
                .containsExactlyInAnyOrder(SourceDomain.TASK, SourceDomain.CHAT, SourceDomain.POST);
    }

    @Test
    void 업무카드_첨부는_임베딩_비교_없이_산출물제출로_분류한다() {
        ReportActivityLog log = contentlessLog(RawActivityType.TASK_ATTACHMENT_ADD, null, 1L);
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 상태변경_완료는_산출물제출로_분류한다() {
        ReportActivityLog log = contentlessLog(
                RawActivityType.TASK_STATUS_CHANGE, "{\"newStatus\":\"DONE\"}", 2L);
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 벡터가_anchor와_유사도가_높으면_해당_카테고리로_분류한다() {
        stubTwoCategoryAnchors();
        ReportActivityLog log = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "리뷰 남겼습니다",
                List.of(1.0f, 0.0f), 3L); // FEEDBACK centroid와 완전히 같은 방향
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.FEEDBACK);
    }

    @Test
    void 유사도가_임계값_미만이면_rawActivityType_기본값으로_폴백한다() {
        stubTwoCategoryAnchors();
        // FEEDBACK=[1,0]과는 반대 방향(유사도 -1), SIMPLE_RESPONSE=[0,1]과는 직교(유사도 0) —
        // 둘 다 임계값(0.5) 미만이라 rawActivityType(CHAT_MESSAGE) 기본값인 SIMPLE_RESPONSE로 폴백.
        ReportActivityLog log = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "아무 의미 없는 문장",
                List.of(-1.0f, 0.0f), 4L);
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.SIMPLE_RESPONSE);
    }

    @Test
    void 임베딩_벡터_파싱에_실패하면_rawActivityType_기본값으로_폴백한다() {
        ReportActivityLog log = ReportActivityLog.create(
                member(), SourceDomain.POST, RawActivityType.POST_CREATE, "게시글 내용",
                LocalDateTime.of(2026, 8, 7, 10, 0), null, null);
        assignId(log, 5L);
        log.applyNoiseFilter(false);
        log.applyEmbedding("gemini-embedding-001", "not-a-valid-json-array");
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 배치_처리한_행_개수를_반환한다() {
        stubTwoCategoryAnchors();
        ReportActivityLog a = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "메시지1", List.of(1.0f, 0.0f), 6L);
        ReportActivityLog b = contentlessLog(RawActivityType.TASK_ATTACHMENT_ADD, null, 7L);
        stubFetch(List.of(a, b));

        int classified = service.classifyBatch();

        assertThat(classified).isEqualTo(2);
    }

    @Test
    void 활동_임베딩_모델이_anchor_모델과_다르면_규칙_폴백으로_처리한다() {
        when(anchorCache.modelName()).thenReturn("gemini-embedding-001");
        ReportActivityLog log = ReportActivityLog.create(
                member(), SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, "예전 모델로 만든 벡터",
                LocalDateTime.of(2026, 8, 7, 10, 0), null, null);
        assignId(log, 10L);
        log.applyNoiseFilter(false);
        log.applyEmbedding("old-embedding-model-v1", writeJson(List.of(1.0f, 0.0f))); // anchor와 다른 모델
        stubFetch(List.of(log));

        service.classifyBatch();

        // 벡터 비교 자체를 건너뛰고 CHAT_MESSAGE의 rawActivityType 기본값으로 폴백된다.
        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.SIMPLE_RESPONSE);
    }

    @Test
    void 빈_벡터_배열은_규칙_폴백으로_처리한다() {
        ReportActivityLog log = embeddedLogWithRawJson(
                RawActivityType.POST_CREATE, SourceDomain.POST, "게시글", "[]", 11L);
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.DELIVERABLE_SUBMIT);
    }

    @Test
    void 벡터에_null_원소가_있으면_규칙_폴백으로_처리한다() {
        ReportActivityLog log = embeddedLogWithRawJson(
                RawActivityType.COMMENT_CREATE, SourceDomain.POST, "댓글", "[1.0, null, 0.5]", 12L);
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.FEEDBACK);
    }

    @Test
    void 영벡터는_규칙_폴백으로_처리한다() {
        ReportActivityLog log = embeddedLogWithRawJson(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "메시지", "[0.0, 0.0]", 13L);
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.SIMPLE_RESPONSE);
    }

    @Test
    void anchor와_차원이_다른_벡터는_예외_없이_규칙_폴백으로_처리하고_배치는_계속된다() {
        when(anchorCache.modelName()).thenReturn("gemini-embedding-001");
        when(anchorCache.cachedCategories()).thenReturn(Set.of(ActivityCategory.FEEDBACK));
        when(anchorCache.centroidOf(ActivityCategory.FEEDBACK)).thenReturn(List.of(1.0f, 0.0f)); // 2차원
        ReportActivityLog log = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "메시지",
                List.of(1.0f, 0.0f, 0.0f), 14L); // 3차원 — anchor와 차원 불일치
        stubFetch(List.of(log));

        int classified = service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.SIMPLE_RESPONSE); // CHAT_MESSAGE 폴백
        assertThat(classified).isEqualTo(1); // 배치 자체는 롤백 없이 정상 완료
    }

    @Test
    void 예상치_못한_예외가_발생한_행은_건너뛰고_이후_행은_계속_분류된다() {
        when(anchorCache.modelName()).thenReturn("gemini-embedding-001");
        when(anchorCache.centroidOf(ActivityCategory.FEEDBACK)).thenReturn(List.of(1.0f, 0.0f));
        when(anchorCache.centroidOf(ActivityCategory.SIMPLE_RESPONSE)).thenReturn(List.of(0.0f, 1.0f));
        // 조회 결과 첫 번째 행(정렬상 더 오래된 행) 처리 중에만 예상치 못한 예외가 나는 상황을 흉내낸다.
        when(anchorCache.cachedCategories())
                .thenThrow(new RuntimeException("예상치 못한 장애"))
                .thenReturn(Set.of(ActivityCategory.FEEDBACK, ActivityCategory.SIMPLE_RESPONSE));
        ReportActivityLog broken = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "먼저 온 문제 있는 행",
                List.of(1.0f, 0.0f), 15L);
        ReportActivityLog healthy = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "뒤에 온 정상 행",
                List.of(1.0f, 0.0f), 16L);
        stubFetch(List.of(broken, healthy)); // 정렬 계약대로 broken이 먼저 온다고 가정

        int classified = service.classifyBatch();

        assertThat(broken.getClassifiedType()).isNull(); // classify()가 호출되지 않아 재처리 대상으로 남음
        assertThat(broken.getClassificationRetryCount()).isEqualTo(1); // 실패 1회 기록
        assertThat(broken.getClassificationNextRetryAt()).isNotNull(); // backoff 시각이 찍힘
        assertThat(broken.isClassificationFailed()).isFalse(); // 아직 최대 재시도 전
        assertThat(healthy.getClassifiedType()).isEqualTo(ActivityCategory.FEEDBACK); // 뒤의 행은 정상 분류
        assertThat(classified).isEqualTo(1);
    }

    @Test
    void 실패가_반복되면_매번_backoff_시각을_갱신하고_최대_횟수를_넘기면_영구_실패로_전환한다() {
        when(anchorCache.modelName()).thenReturn("gemini-embedding-001");
        when(anchorCache.cachedCategories()).thenThrow(new RuntimeException("계속 실패"));
        ReportActivityLog log = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "계속 실패하는 행",
                List.of(1.0f, 0.0f), 21L);
        stubFetch(List.of(log));

        // MAX_RETRY_COUNT=5 — 1~4번째 실패까지는 backoff만 찍히고 계속 재처리 대상으로 남는다.
        for (int attempt = 1; attempt <= 5; attempt++) {
            service.classifyBatch();

            assertThat(log.getClassificationRetryCount()).isEqualTo(attempt);
            assertThat(log.getClassificationNextRetryAt()).isNotNull();
            assertThat(log.isClassificationFailed()).isFalse();
            assertThat(log.getClassifiedType()).isNull();
        }

        service.classifyBatch(); // 5번째 실패 — 영구 실패로 전환

        assertThat(log.getClassificationRetryCount()).isEqualTo(5);
        assertThat(log.isClassificationFailed()).isTrue();
        assertThat(log.getClassificationNextRetryAt()).isNull();
        assertThat(log.getClassifiedType()).isNull();
    }

    @Test
    void 성공하면_이전에_쌓인_재시도_상태가_초기화된다() {
        stubTwoCategoryAnchors();
        ReportActivityLog log = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "리뷰 남겼습니다",
                List.of(1.0f, 0.0f), 22L);
        log.scheduleClassificationRetry(LocalDateTime.now().plusMinutes(5)); // 이전 실패 흔적을 미리 만들어둠
        stubFetch(List.of(log));

        service.classifyBatch();

        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.FEEDBACK);
        assertThat(log.getClassificationRetryCount()).isZero();
        assertThat(log.getClassificationNextRetryAt()).isNull();
        assertThat(log.isClassificationFailed()).isFalse();
    }

    @Test
    void 유사도가_동점이면_enum_선언_순서가_빠른_카테고리를_결정적으로_선택한다() {
        when(anchorCache.modelName()).thenReturn("gemini-embedding-001");
        // Set 리터럴의 내부 순서를 일부러 PROBLEM_SOLVING이 먼저 오게 섞어도 결과가 바뀌면 안 된다.
        when(anchorCache.cachedCategories())
                .thenReturn(Set.of(ActivityCategory.PROBLEM_SOLVING, ActivityCategory.DECISION));
        when(anchorCache.centroidOf(ActivityCategory.DECISION)).thenReturn(List.of(1.0f, 0.0f));
        when(anchorCache.centroidOf(ActivityCategory.PROBLEM_SOLVING)).thenReturn(List.of(0.0f, 1.0f));
        // [1,1]은 두 centroid와 코사인 유사도가 1/√2로 완전히 동일한 진짜 동점 상황이다.
        ReportActivityLog log = embeddedLog(
                RawActivityType.CHAT_MESSAGE, SourceDomain.CHAT, "동점 유사도 테스트",
                List.of(1.0f, 1.0f), 23L);
        stubFetch(List.of(log));

        service.classifyBatch();

        // ActivityCategory 선언 순서상 DECISION이 PROBLEM_SOLVING보다 먼저이므로 항상 DECISION이 이긴다.
        assertThat(log.getClassifiedType()).isEqualTo(ActivityCategory.DECISION);
    }
}