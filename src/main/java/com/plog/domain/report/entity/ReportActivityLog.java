package com.plog.domain.report.entity;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.task.entity.Task;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 리포트 파이프라인(0~4단계) 공통 활동 로그. 활동 1건 = 행 1개.
 * 0단계(수집)에서 각 도메인이 행을 생성하고, 1~3단계(정제/분류/연결/임베딩)는 내부 도메인
 * (TASK/CHAT/POST)에 대해서만 {@link #applyNoiseFilter}, {@link #classify},
 * {@link #linkTask}, {@link #applyEmbedding}이 순서대로 호출된다. 외부 도메인(GITHUB/FIGMA/NOTION/GOOGLE)은
 * rawActivityType 자체가 이미 세분류라 이 단계들을 거치지 않고 바로 4단계 점수 계산에 쓰인다.
 * 이 계약은 아래 메서드들이 직접 검증한다 — 위반하면 IllegalStateException.
 * 레거시 {@code activity_log} 테이블은 resource_id가 필수라 재사용하지 않고 report 도메인에 별도로 둔다.
 */
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "report_activity_log", uniqueConstraints = {
        // 같은 원본 이벤트가 재발행(리스너 재시도, 동기화 재실행 등)되어도 중복 적재되지 않도록.
        // sourceRefId가 없는 도메인(현재는 없지만 대비)은 null 허용 — Postgres는 null끼리 unique 위반으로 안 봄.
        @UniqueConstraint(name = "uk_report_activity_source", columnNames = {"source_domain", "source_ref_id"})
})
public class ReportActivityLog extends BaseEntity {

    /** 1~3단계(정제/분류/연결/임베딩) 대상 도메인. 그 외(외부 연동)는 rawActivityType이 이미 세분류라 대상이 아니다. */
    private static final Set<SourceDomain> REFINABLE_DOMAINS =
            EnumSet.of(SourceDomain.TASK, SourceDomain.CHAT, SourceDomain.POST);

    /** 정제 후에도 임베딩할 텍스트가 없는 행(예: content가 없는 TASK_STATUS_CHANGE)의 embeddingModel 값. */
    private static final String EMBEDDING_NOT_APPLICABLE = "N/A";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_activity_log_id")
    private Long id;

    // 활동 주체. 외부 계정 매핑이 안 된 시점에 수집될 수도 있어 nullable로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id")
    private ProjectMember projectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_domain", nullable = false, length = 20)
    private SourceDomain sourceDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "raw_activity_type", nullable = false, length = 40)
    private RawActivityType rawActivityType;

    // 정제/분류 대상 원문. 텍스트가 없는 유형(TASK_STATUS_CHANGE, 각종 SNAPSHOT 등)은 null.
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // 0단계에서 원천이 이미 알고 있으면 채워서 넘겨줌, 없으면 3단계에서 채움.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_task_id")
    private Task linkedTask;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // 첨부여부, 원본 점수, 마감일 등 도메인별 부가정보.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    // 원본 이벤트의 고유 식별자(채팅 메시지 ID, 커밋 SHA 등 문자열화).
    // 위 uk_report_activity_source 제약의 idempotency key로 사용.
    @Column(name = "source_ref_id")
    private String sourceRefId;

    /** 1단계 정제 결과. 아직 정제 전이면 null, 노이즈면 true, 노이즈 아니면 false. */
    @Column(name = "noise_filtered")
    private Boolean noiseFiltered;

    /** 2단계 분류 결과. 내부 도메인 + 정제 통과(noiseFiltered=false) 행에만 값이 채워지고 그 외는 항상 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "classified_type", length = 30)
    private ActivityCategory classifiedType;

    /**
     * 3단계 임베딩 생성에 쓰인 모델명. 실제 벡터가 있으면 모델명, 임베딩할 텍스트 자체가 없어
     * ({@link #content}가 정제 후에도 비면) 처리 완료 표시만 한 경우는 {@link #EMBEDDING_NOT_APPLICABLE}.
     * 아직 처리 전이면 null — 배치 조회의 "아직 안 한 것" 판별 기준이 된다.
     */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /** 임베딩 벡터(JSON 배열 텍스트). embeddingModel이 실제 모델명일 때만 값이 있다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "embedding", columnDefinition = "jsonb")
    private String embedding;

    /**
     * 임베딩 배치가 이 행을 "처리 중"으로 선점(lease)한 만료 시각. 이 값이 미래면 다른 배치
     * 호출이 같은 행을 다시 집어가지 않는다 — 동시 배치 실행이 같은 활동에 중복으로 API를
     * 호출하는 걸 막기 위함. 처리(성공/처리불필요)가 끝나면 null로 되돌린다. 앱이 죽는 등으로
     * 처리가 안 끝난 채 남아도, 이 시각이 지나면 자동으로 다시 선점 대상이 된다(복구 가능).
     */
    @Column(name = "embedding_lease_until")
    private LocalDateTime embeddingLeaseUntil;

    /**
     * {@link #embeddingLeaseUntil}과 함께 찍히는 소유권 토큰(배치 1회 호출당 UUID 하나).
     * 리스를 해제·갱신할 때 이 토큰이 일치하는 행만 건드린다 — 이미 만료돼서 다른 실행자가
     * 새로 선점한 행을 실수로 건드리지 않기 위한 낙관적 동시성 체크다.
     */
    @Column(name = "embedding_lease_token", length = 36)
    private String embeddingLeaseToken;

    /**
     * 2단계 분류 실패 횟수. 분류 자체는 실패해도 배치를 죽이면 안 되므로, 실패한 행은
     * classifiedType=null로 남되 이 카운트를 올려서 몇 번째 재시도인지 추적한다. 성공하면
     * {@link #classify}에서 0으로 되돌린다.
     */
    @Column(name = "classification_retry_count", nullable = false, columnDefinition = "integer default 0")
    private int classificationRetryCount;

    /**
     * 다음 분류 재시도 가능 시각(backoff). 이 값이 미래면 배치 조회 대상에서 제외된다 —
     * 오래된 실패 행 하나가 정렬(occurredAt ASC)상 계속 맨 앞에 걸려 매 배치의 Limit을
     * 정상 행 대신 소모하는 것을 막기 위함이다. null이면 즉시 재시도 가능.
     */
    @Column(name = "classification_next_retry_at")
    private LocalDateTime classificationNextRetryAt;

    /**
     * 최대 재시도 횟수를 넘겨 더 이상 자동 재시도하지 않는 상태. true인 행은 배치 조회 대상에서
     * 영구히 제외된다 — classifiedType은 null로 남으므로 activityTypeSummary/competencyEvidence
     * 집계에서는 자동으로 빠지고, 별도 모니터링/수동 개입으로만 복구 가능하다.
     */
    @Column(name = "classification_failed", nullable = false, columnDefinition = "boolean default false")
    private boolean classificationFailed;

    public static ReportActivityLog create(
            ProjectMember projectMember,
            SourceDomain sourceDomain,
            RawActivityType rawActivityType,
            String content,
            LocalDateTime occurredAt,
            String metadata,
            String sourceRefId
    ) {
        return create(projectMember, sourceDomain, rawActivityType, content, occurredAt, metadata, sourceRefId, null);
    }

    /**
     * {@link #linkedTask}까지 0단계에서 바로 채우는 오버로드. TASK 도메인 이벤트는 애초에 어느
     * 업무카드에서 발생했는지 확실히 알고 있어(예: 상태변경/첨부 이벤트가 taskId를 직접 들고 옴)
     * 3단계 {@link #linkTask} 의 매칭 추론이 필요 없다 — 그래서 생성 시점에 바로 확정한다.
     * Chat/Post/Evaluation처럼 원천이 어느 업무카드인지 모르는 도메인은 기존 7-파라미터
     * {@code create}(linkedTask=null)를 그대로 쓰면 된다.
     */
    public static ReportActivityLog create(
            ProjectMember projectMember,
            SourceDomain sourceDomain,
            RawActivityType rawActivityType,
            String content,
            LocalDateTime occurredAt,
            String metadata,
            String sourceRefId,
            Task linkedTask
    ) {
        if (sourceDomain == null || rawActivityType == null || occurredAt == null) {
            throw new IllegalArgumentException("sourceDomain, rawActivityType, occurredAt은 필수입니다.");
        }
        if (rawActivityType.owningDomain() != sourceDomain) {
            throw new IllegalArgumentException(
                    "sourceDomain과 rawActivityType 조합이 올바르지 않습니다. sourceDomain=" + sourceDomain
                            + ", rawActivityType=" + rawActivityType
                            + "(소유 도메인=" + rawActivityType.owningDomain() + ")");
        }
        return ReportActivityLog.builder()
                .projectMember(projectMember)
                .sourceDomain(sourceDomain)
                .rawActivityType(rawActivityType)
                .content(content)
                .occurredAt(occurredAt)
                .metadata(metadata)
                .sourceRefId(sourceRefId)
                .linkedTask(linkedTask)
                .build();
    }

    /**
     * 1단계 정제 결과 기록. 내부 도메인(TASK/CHAT/POST)에만 적용 가능 — 외부 활동은 애초에
     * rawActivityType이 이미 세분류라 정제 대상이 아니다.
     */
    public void applyNoiseFilter(boolean noiseFiltered) {
        if (!REFINABLE_DOMAINS.contains(this.sourceDomain)) {
            throw new IllegalStateException(
                    "정제는 내부 도메인(TASK/CHAT/POST)에만 적용할 수 있습니다. sourceDomain=" + sourceDomain);
        }
        this.noiseFiltered = noiseFiltered;
    }

    /**
     * 2단계 분류 결과 기록. 정제를 거쳐 noiseFiltered=false로 확정된 행에만 호출 가능.
     * 이전에 실패해서 쌓인 재시도 카운트/backoff/영구실패 표시가 있었다면 성공한 이상 의미가
     * 없으므로 함께 초기화한다.
     */
    public void classify(ActivityCategory classifiedType) {
        requireRefined();
        this.classifiedType = classifiedType;
        this.classificationRetryCount = 0;
        this.classificationNextRetryAt = null;
        this.classificationFailed = false;
    }

    /**
     * 3단계 업무카드 연결. 정제를 거쳐 noiseFiltered=false로 확정된 행에만 호출 가능.
     * 0단계에서 이미 linkedTask가 채워져 있으면 다시 덮어쓰지 않는다.
     */
    public void linkTask(Task task) {
        requireRefined();
        if (this.linkedTask != null) {
            return;
        }
        this.linkedTask = task;
    }

    /**
     * classify/linkTask 공통 선행조건. noiseFiltered가 정확히 false(정제 통과 확정)일 때만 통과시킨다.
     * null(정제 미실행)이나 true(노이즈로 판정됨)인 상태에서 분류/연결이 저장되는 걸 막는다.
     */
    private void requireRefined() {
        if (!Boolean.FALSE.equals(this.noiseFiltered)) {
            throw new IllegalStateException(
                    "정제 결과가 noiseFiltered=false로 확정된 행에만 분류/연결을 적용할 수 있습니다. "
                            + "현재 noiseFiltered=" + noiseFiltered);
        }
    }

    /** 정제 결과 "노이즈"로 판정돼 2~3단계 대상에서 제외되는지 여부. */
    public boolean isNoise() {
        return Boolean.TRUE.equals(noiseFiltered);
    }

    /**
     * 3단계 임베딩 결과 기록. 정제를 거쳐 noiseFiltered=false로 확정된 행에만 호출 가능.
     * 임베딩할 정제된 텍스트가 없는 행은 이 메서드가 아니라 {@link #markEmbeddingNotApplicable()}를 쓴다.
     */
    public void applyEmbedding(String model, String embeddingJson) {
        requireRefined();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model은 필수입니다.");
        }
        if (embeddingJson == null || embeddingJson.isBlank()) {
            throw new IllegalArgumentException("embeddingJson은 필수입니다.");
        }
        this.embeddingModel = model;
        this.embedding = embeddingJson;
        this.embeddingLeaseUntil = null; // 완료됐으니 리스 해제
        this.embeddingLeaseToken = null;
    }

    /**
     * 정제를 통과했지만(noiseFiltered=false) 임베딩할 텍스트 자체가 없는 행(예: content가 없는
     * TASK_STATUS_CHANGE)의 3단계 처리를 "완료"로 표시한다. 이 표시가 없으면 배치 조회가 매번
     * 같은 행을 다시 골라내 반복 처리하게 된다.
     */
    public void markEmbeddingNotApplicable() {
        requireRefined();
        this.embeddingModel = EMBEDDING_NOT_APPLICABLE;
        this.embedding = null;
        this.embeddingLeaseUntil = null; // 완료됐으니 리스 해제
        this.embeddingLeaseToken = null;
    }

    /** 실제 임베딩 벡터가 채워져 있는지. markEmbeddingNotApplicable만 호출된 행은 false. */
    public boolean hasEmbedding() {
        return embedding != null;
    }

    /**
     * 2단계 분류가 실패해서 다음 backoff 시각까지 재시도를 미룬다. 재시도 횟수를 올리고
     * nextRetryAt을 찍는다 — 몇 번째 실패인지는 {@link #getClassificationRetryCount()}로 호출부가
     * 읽어서 최대 재시도 초과 여부(→ {@link #markClassificationFailed()} 호출 여부)를 판단한다.
     */
    public void scheduleClassificationRetry(LocalDateTime nextRetryAt) {
        requireRefined();
        this.classificationRetryCount++;
        this.classificationNextRetryAt = nextRetryAt;
    }

    /**
     * 최대 재시도 횟수를 넘겨 더 이상 자동 재시도하지 않기로 확정한다. 이 행은 배치 조회
     * 대상에서 영구히 빠지므로 이후 별도 모니터링/수동 개입으로만 복구할 수 있다.
     */
    public void markClassificationFailed() {
        requireRefined();
        this.classificationRetryCount++;
        this.classificationFailed = true;
        this.classificationNextRetryAt = null;
    }
}