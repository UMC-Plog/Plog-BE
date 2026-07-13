package com.plog.domain.evaluation.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "peer_reviews")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeerReview extends BaseEntity {

    @Id
    @Column(name = "peer_id", nullable = false)
    private String peerId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "evaluator_id", nullable = false)
    private String evaluatorId;

    @Column(name = "evaluatee_id", nullable = false)
    private String evaluateeId;

    @Column(name = "collaboration_score", nullable = false)
    private Integer collaborationScore;

    @Column(name = "initiative_score", nullable = false)
    private Integer initiativeScore;

    @Column(name = "communication_score", nullable = false)
    private Integer communicationScore;

    @Column(name = "responsibility_score", nullable = false)
    private Integer responsibilityScore;

    @Column(name = "output_score", nullable = false)
    private Integer outputScore;

    @Column(nullable = false, length = 1000)
    private String feedback;
}
