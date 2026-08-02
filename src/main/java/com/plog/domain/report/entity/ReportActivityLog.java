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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 리포트 파이프라인(0~4단계) 공통 활동 로그. 활동 1건 = 행 1개.
 * <p>
 * 0단계(수집)에서 각 도메인이 행을 생성하고, 1~3단계(정제/분류/연결)는 내부 도메인
 * (TASK/CHAT/POST)에 대해서만 {@link #applyNoiseFilter}, {@link #classify},
 * {@link #linkTask}가 순서대로 호출된다. 외부 도메인(GITHUB/FIGMA/NOTION/GOOGLE)은
 * rawActivityType 자체가 이미 세분류라 이 세 단계를 거치지 않고 바로 4단계 점수 계산에 쓰인다.
 * <p>
 * 기존 {@code domain.integration.entity.ActivityLog}는 resource_id가 필수라 재사용하지 않고
 * report 도메인에 별도로 둔다.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_activity_log_id")
    private Long id;

    // 활동 주체. 외부 계정 매핑이 안 된 시점에 수집될 수도 있어 nullable로 둔다
    // (ActivityLog.projectMember와 동일한 이유).
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

    // 0단계에서 원천이 이미 알고 있으면 채워서 넘겨줌(있으면 3단계 부담↓), 없으면 3단계에서 채움.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_task_id")
    private Task linkedTask;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    // 첨부여부, 원본 점수, 마감일 등 도메인별 부가정보.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    // 원본 이벤트의 고유 식별자(채팅 메시지 ID, 업무 상태변경 이벤트 ID 등 문자열화).
    // 위 uk_report_activity_source 제약의 idempotency key로 사용. 원문 스키마엔 없던 필드라 추가 시 팀 공유 필요.
    @Column(name = "source_ref_id")
    private String sourceRefId;

    /** 1단계 정제 결과. 아직 정제 전이면 null, 노이즈로 판정되면 true. */
    @Column(name = "noise_filtered")
    private Boolean noiseFiltered;

    /** 2단계 분류 결과. 내부 도메인만 값이 채워지고 외부 도메인은 항상 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "classified_type", length = 30)
    private ActivityCategory classifiedType;

    public static ReportActivityLog create(
            ProjectMember projectMember,
            SourceDomain sourceDomain,
            RawActivityType rawActivityType,
            String content,
            LocalDateTime occurredAt,
            String metadata,
            String sourceRefId
    ) {
        if (sourceDomain == null || rawActivityType == null || occurredAt == null) {
            throw new IllegalArgumentException("sourceDomain, rawActivityType, occurredAt은 필수입니다.");
        }
        return ReportActivityLog.builder()
                .projectMember(projectMember)
                .sourceDomain(sourceDomain)
                .rawActivityType(rawActivityType)
                .content(content)
                .occurredAt(occurredAt)
                .metadata(metadata)
                .sourceRefId(sourceRefId)
                .build();
    }

    /** 1단계 정제 결과 기록. */
    public void applyNoiseFilter(boolean noiseFiltered) {
        this.noiseFiltered = noiseFiltered;
    }

    /** 2단계 분류 결과 기록. 정제를 거치지 않은(=noiseFiltered가 null인) 행에는 호출하지 않는다. */
    public void classify(ActivityCategory classifiedType) {
        this.classifiedType = classifiedType;
    }

    /** 3단계 업무카드 연결. 0단계에서 이미 linkedTask가 채워져 있으면 다시 덮어쓰지 않는다. */
    public void linkTask(Task task) {
        if (this.linkedTask != null) {
            return;
        }
        this.linkedTask = task;
    }

    /** 정제 결과 "노이즈"로 판정돼 2~3단계 대상에서 제외되는지 여부. */
    public boolean isNoise() {
        return Boolean.TRUE.equals(noiseFiltered);
    }
}