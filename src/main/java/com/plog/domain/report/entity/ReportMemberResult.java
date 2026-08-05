package com.plog.domain.report.entity;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "report_member_result", uniqueConstraints = {
        // 한 리포트에 같은 멤버의 결과는 1건만
        @UniqueConstraint(name = "uk_report_member", columnNames = {"report_id", "project_member_id"})
})
public class ReportMemberResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Column(name = "internal_score", precision = 5, scale = 2)
    private BigDecimal internalScore;

    @Column(name = "external_score", precision = 5, scale = 2)
    private BigDecimal externalScore;

    @Column(name = "peer_score", precision = 5, scale = 2)
    private BigDecimal peerScore;

    @Column(name = "self_feedback_score", precision = 5, scale = 2)
    private BigDecimal selfFeedbackScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "external_tool_connected", nullable = false)
    private boolean externalToolConnected;

    @Enumerated(EnumType.STRING)
    @Column(name = "reliability_tier", length = 10)
    private ReliabilityTier reliabilityTier;

    @Column(name = "caution_text", columnDefinition = "TEXT")
    private String cautionText;

    public static ReportMemberResult create(Report report, ProjectMember projectMember) {
        if (report == null || projectMember == null) {
            throw new IllegalArgumentException("report and projectMember must not be null");
        }
        return ReportMemberResult.builder().report(report).projectMember(projectMember).build();
    }

    public void applyScores(
            BigDecimal internalScore,
            BigDecimal externalScore,
            BigDecimal peerScore,
            BigDecimal selfFeedbackScore,
            BigDecimal finalScore,
            boolean externalToolConnected,
            ReliabilityTier reliabilityTier,
            String cautionText
    ) {
        this.internalScore = internalScore;
        this.externalScore = externalScore;
        this.peerScore = peerScore;
        this.selfFeedbackScore = selfFeedbackScore;
        this.finalScore = finalScore;
        this.externalToolConnected = externalToolConnected;
        this.reliabilityTier = reliabilityTier;
        this.cautionText = cautionText;
    }
}
