package com.plog.domain.report.entity;

import com.plog.domain.project.entity.Project;
import com.plog.global.common.BaseEntity;
import com.plog.global.util.TimeUtil;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * 프로젝트 리포트 1건. 상태 전이 규칙은 {@link ReportStatus} 의 표에 정리돼 있고,
 * 그 규칙을 강제하는 코드가 이 클래스의 start/complete/fail/attachPdf 다.
 * status 를 밖에서 바꿀 수 있는 통로(setter)는 두지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(name = Report.UNIQUE_PROJECT_REPORT_CONSTRAINT, columnNames = "project_id")
})
public class Report extends BaseEntity {

    public static final String UNIQUE_PROJECT_REPORT_CONSTRAINT = "uk_report_project";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    // 팀/개인 리포트는 같은 분석 결과를 서로 다른 형태로 보여주는 것이므로 프로젝트당 한 행만 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'GENERATING'")
    private ReportStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @Column(name = "pdf_object_key", length = 1024)
    private String pdfObjectKey;

    @Column(name = "pdf_file_name")
    private String pdfFileName;

    // 팀 리포트 상단 "AI 인사이트". 프로젝트 단위 텍스트라 멤버별 테이블에 둘 수 없다
    // (ReportMemberResult 에 넣으면 같은 문장이 멤버 수만큼 중복 저장된다).
    @Column(name = "team_strength", columnDefinition = "TEXT")
    private String teamStrength;

    @Column(name = "team_suggestion", columnDefinition = "TEXT")
    private String teamSuggestion;

    @Column(name = "team_completion_rate", precision = 5, scale = 2)
    private BigDecimal teamCompletionRate;

    @Column(name = "team_deadline_compliance_rate", precision = 5, scale = 2)
    private BigDecimal teamDeadlineComplianceRate;

    private Report(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        this.project = project;
        this.status = ReportStatus.GENERATING;
        this.snapshotAt = TimeUtil.now();
    }

    public static Report start(Project project) {
        return new Report(project);
    }

    /** 실패한 생성 시도를 같은 행에서 다시 시작한다. 발행 완료 리포트는 재시작할 수 없다. */
    public void restart() {
        if (status != ReportStatus.FAILED) {
            throw new IllegalStateException(
                    "only a failed report can restart, but status=" + status);
        }
        this.status = ReportStatus.GENERATING;
        this.snapshotAt = TimeUtil.now();
        this.completedAt = null;
        this.pdfObjectKey = null;
        this.pdfFileName = null;
        this.teamStrength = null;
        this.teamSuggestion = null;
        this.teamCompletionRate = null;
        this.teamDeadlineComplianceRate = null;
    }

    public void complete(LocalDateTime completedAt) {
        requireGenerating();
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        this.status = ReportStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    /**
     * 팀 인사이트 기록. 생성 중인 리포트에만 쓸 수 있다 — 발행 후 문구가 바뀌면
     * 이미 내려간 리포트의 내용이 사후에 달라진다.
     * <p>
     * 팀 인사이트 생성 실패는 리포트 발행을 막지 않는다(멤버별 결과가 본체다).
     * 그래서 null 을 허용하고, 화면은 비어 있으면 섹션을 숨긴다.
     */
    public void applyTeamInsight(String strength, String suggestion) {
        requireGenerating();
        this.teamStrength = strength;
        this.teamSuggestion = suggestion;
    }

    public void applyTeamRates(BigDecimal completionRate, BigDecimal deadlineComplianceRate) {
        requireGenerating();
        this.teamCompletionRate = completionRate;
        this.teamDeadlineComplianceRate = deadlineComplianceRate;
    }

    public String getTeamReportCode() {
        return ReportCodeFormatter.formatTeam(project.getId(), getCreatedAt());
    }

    public String getPersonalReportCode() {
        return ReportCodeFormatter.formatPersonal(project.getId(), getCreatedAt());
    }

    public void attachPdf(String objectKey, String fileName) {
        if (status != ReportStatus.COMPLETED) {
            throw new IllegalStateException("PDF metadata can be attached only to a completed report");
        }
        String safeObjectKey = validateObjectKey(objectKey);
        String safeFileName = validateFileName(fileName);
        this.pdfObjectKey = safeObjectKey;
        this.pdfFileName = safeFileName;
    }

    public void fail() {
        requireGenerating();
        this.status = ReportStatus.FAILED;
        this.completedAt = null;
        this.pdfObjectKey = null;
        this.pdfFileName = null;
    }

    @PrePersist
    private void initializeStatus() {
        if (status == null) {
            status = ReportStatus.GENERATING;
        }
    }

    private void requireGenerating() {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "only a generating report can transition state, but status=" + status);
        }
    }

    private String validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("PDF object key must not be blank");
        }
        String trimmed = objectKey.trim();
        String lowerCase = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/") || trimmed.contains("\\") || trimmed.contains("..")
                || lowerCase.startsWith("http://") || lowerCase.startsWith("https://")) {
            throw new IllegalArgumentException("PDF object key must be a safe S3 object key");
        }
        return trimmed;
    }

    private String validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("PDF file name must not be blank");
        }
        String trimmed = fileName.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")
                || !trimmed.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("report archive file name must be a safe .zip name");
        }
        return trimmed;
    }
}
