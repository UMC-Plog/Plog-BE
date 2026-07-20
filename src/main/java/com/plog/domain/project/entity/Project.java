package com.plog.domain.project.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "projects", uniqueConstraints = {
        // 초대 토큰으로 프로젝트를 특정해야 하므로 중복 불가
        @UniqueConstraint(name = "uk_project_invite_token", columnNames = "invite_token_hash")
})
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "invite_token_hash", nullable = false)
    private String inviteTokenHash;

    @Column(name = "invite_token_encrypted", length = 1024)
    private String inviteTokenEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;

    @Column(name = "start_day", nullable = false)
    private LocalDate startDay;

    @Column(name = "end_day", nullable = false)
    private LocalDate endDay;

    @Builder
    private Project(
            Long id,
            String projectName,
            String inviteTokenHash,
            String inviteTokenEncrypted,
            ProjectType projectType,
            ProjectStatus status,
            LocalDate startDay,
            LocalDate endDay
    ) {
        validateInviteTokenValues(inviteTokenHash, inviteTokenEncrypted);
        this.id = id;
        this.projectName = projectName;
        this.inviteTokenHash = inviteTokenHash;
        this.inviteTokenEncrypted = inviteTokenEncrypted;
        this.projectType = projectType;
        this.status = status;
        this.startDay = startDay;
        this.endDay = endDay;
    }

    public void updateSettings(String projectName, LocalDate endDay, ProjectType projectType) {
        if (projectName != null) {
            this.projectName = projectName;
        }
        if (endDay != null) {
            this.endDay = endDay;
        }
        if (projectType != null) {
            this.projectType = projectType;
        }
    }

    public void rotateInviteToken(String inviteTokenHash, String inviteTokenEncrypted) {
        validateInviteTokenValues(inviteTokenHash, inviteTokenEncrypted);
        this.inviteTokenHash = inviteTokenHash;
        this.inviteTokenEncrypted = inviteTokenEncrypted;
    }

    private static void validateInviteTokenValues(String inviteTokenHash, String inviteTokenEncrypted) {
        if (inviteTokenHash == null || inviteTokenHash.isBlank()
                || inviteTokenEncrypted == null || inviteTokenEncrypted.isBlank()) {
            throw new IllegalArgumentException("invite token values must not be blank");
        }
    }

    public boolean isEvaluatingState() {
        if (this.status == ProjectStatus.COMPLETED) {
            return false;
        }
        return !LocalDate.now().isAfter(this.endDay);
    }
}
