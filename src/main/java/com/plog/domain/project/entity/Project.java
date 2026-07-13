package com.plog.domain.project.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "projects")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {

    @Id
    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "invite_token_hash", nullable = false)
    private String inviteTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Column(name = "start_day", nullable = false)
    private LocalDate startDay;

    @Column(name = "end_day", nullable = false)
    private LocalDate endDay;

    public boolean isEvaluationReady() {
        return status == ProjectStatus.EVALUATING;
    }
}
