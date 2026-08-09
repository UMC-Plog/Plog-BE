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

    private String writeJson(List<Float> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubFetch(List<ReportActivityLog> logs) {
        when(activityLogRepository
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        any(), any(Limit.class)))
                .thenReturn(logs);
    }

    /** anchorCache가 FEEDBACK=[1,0], SIMPLE_RESPONSE=[0,1] centroid를 갖는다고 가정. */
    private void stubTwoCategoryAnchors() {
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
                .findBySourceDomainInAndNoiseFilteredFalseAndEmbeddingModelIsNotNullAndClassifiedTypeIsNullOrderByOccurredAtAscIdAsc(
                        domainsCaptor.capture(), any(Limit.class));
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
}