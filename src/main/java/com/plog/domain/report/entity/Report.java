package com.plog.domain.report.entity;

import com.plog.domain.project.entity.Project;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reports")
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    // 유니크 아님: 한 프로젝트가 중간/최종 등 여러 리포트를 가질 수 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'GENERATING'")
    private ReportStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "pdf_object_key", length = 1024)
    private String pdfObjectKey;

    @Column(name = "pdf_file_name")
    private String pdfFileName;

    private Report(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project must not be null");
        }
        this.project = project;
        this.status = ReportStatus.GENERATING;
    }

    public static Report start(Project project) {
        return new Report(project);
    }

    public void complete(LocalDateTime completedAt) {
        requireGenerating();
        if (completedAt == null) {
            throw new IllegalArgumentException("completedAt must not be null");
        }
        this.status = ReportStatus.COMPLETED;
        this.completedAt = completedAt;
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
        if (status != ReportStatus.GENERATING) {
            throw new IllegalStateException("only a generating report can transition state");
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
                || !trimmed.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("PDF file name must be a safe .pdf name");
        }
        return trimmed;
    }
}
