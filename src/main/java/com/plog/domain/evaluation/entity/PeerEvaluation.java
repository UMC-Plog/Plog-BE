package com.plog.domain.evaluation.entity;

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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "peer_evaluations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_peer_evaluator_evaluatee", columnNames = {"evaluator_id", "evaluatee_id"})
})
public class PeerEvaluation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "peer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private ProjectMember evaluator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluatee_id", nullable = false)
    private ProjectMember evaluatee;

    @Column(name = "collaboration_score", nullable = false)
    private int collaborationScore;

    @Column(name = "initiative_score", nullable = false)
    private int initiativeScore;

    @Column(name = "responsibility_score", nullable = false)
    private int responsibilityScore;

    @Column(name = "communication_score", nullable = false)
    private int communicationScore;

    @Column(name = "output_score", nullable = false)
    private int outputScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", columnDefinition = "jsonb")
    private List<String> keywords;

    @Column(name = "feedback", length = 200)
    private String feedback;
}