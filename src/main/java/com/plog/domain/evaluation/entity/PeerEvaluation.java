package com.plog.domain.evaluation.entity;

import com.plog.domain.project.entity.Project;
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

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "peer_evaluations", uniqueConstraints = {
        // 같은 평가자가 같은 피평가자를 중복 평가 불가 (제출 시점에만 insert → row 존재 = 제출 완료)
        @UniqueConstraint(name = "uk_peer_evaluator_evaluatee", columnNames = {"evaluator_id", "evaluatee_id"})
})
public class PeerEvaluation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "peer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private ProjectMember evaluator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluatee_id", nullable = false)
    private ProjectMember evaluatee;

    @Column(name = "collaboration_score", nullable = false)
    private int collaborationScore;

    // TODO(팀 확인): "주도성"의 영문으로 scrupulosity는 오역(꼼꼼함/강박).
    //  initiative_score 권장. 일단 ERD대로 유지.
    @Column(name = "scrupulosity_score", nullable = false)
    private int scrupulosityScore;

    @Column(name = "responsibility_score", nullable = false)
    private int responsibilityScore;

    @Column(name = "communication_score", nullable = false)
    private int communicationScore;

    @Column(name = "output_score", nullable = false)
    private int outputScore;
}
