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

    /**
     * 평가 마감까지의 유예 기간. 이 기간이 지나면 전원이 제출하지 않았어도 평가를 닫고 리포트를 확정한다.
     * 사용자 요청 경로(ProjectStatusService)와 리포트 자동 생성 배치가 <b>같은 값</b>을 봐야 하므로
     * 서비스가 아니라 엔티티에 둔다 — 한쪽만 바뀌면 "배치는 리포트를 만들었는데 평가는 아직 열려 있는"
     * 상태가 생긴다.
     */
    private static final long EVALUATION_TIMEOUT_DAYS = 7L;

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

    @Getter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "peer_evaluation_status")
    private PeerEvaluationStatus peerEvaluationStatus = PeerEvaluationStatus.PENDING;

    @Getter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "internal_collection_status")
    private ProjectCollectionStatus internalCollectionStatus = ProjectCollectionStatus.NOT_STARTED;

    @Getter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "external_collection_status")
    private ProjectCollectionStatus externalCollectionStatus = ProjectCollectionStatus.NOT_STARTED;

    @Builder
    private Project(
            Long id,
            String projectName,
            String inviteTokenHash,
            String inviteTokenEncrypted,
            ProjectType projectType,
            ProjectStatus status,
            LocalDate startDay,
            LocalDate endDay,
            PeerEvaluationStatus peerEvaluationStatus,
            ProjectCollectionStatus internalCollectionStatus,
            ProjectCollectionStatus externalCollectionStatus
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
        this.peerEvaluationStatus = peerEvaluationStatus == null
                ? PeerEvaluationStatus.PENDING : peerEvaluationStatus;
        this.internalCollectionStatus = internalCollectionStatus == null
                ? ProjectCollectionStatus.NOT_STARTED : internalCollectionStatus;
        this.externalCollectionStatus = externalCollectionStatus == null
                ? ProjectCollectionStatus.NOT_STARTED : externalCollectionStatus;
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

    public void complete() {
        this.status = ProjectStatus.COMPLETED;
        closePeerEvaluation();
    }

    public boolean isCompleted() {
        return this.status == ProjectStatus.COMPLETED;
    }

    public PeerEvaluationStatus getPeerEvaluationStatus() {
        return peerEvaluationStatus == null ? PeerEvaluationStatus.PENDING : peerEvaluationStatus;
    }

    public ProjectCollectionStatus getInternalCollectionStatus() {
        return internalCollectionStatus == null
                ? ProjectCollectionStatus.NOT_STARTED : internalCollectionStatus;
    }

    public ProjectCollectionStatus getExternalCollectionStatus() {
        return externalCollectionStatus == null
                ? ProjectCollectionStatus.NOT_STARTED : externalCollectionStatus;
    }

    public void queueInternalCollection() {
        if (getInternalCollectionStatus() == ProjectCollectionStatus.NOT_STARTED) {
            internalCollectionStatus = ProjectCollectionStatus.PENDING;
        }
    }

    public void startInternalCollection() {
        if (getInternalCollectionStatus() == ProjectCollectionStatus.PENDING
                || getInternalCollectionStatus() == ProjectCollectionStatus.FAILED) {
            internalCollectionStatus = ProjectCollectionStatus.RUNNING;
        }
    }

    public void finishInternalCollection(boolean succeeded) {
        internalCollectionStatus = succeeded
                ? ProjectCollectionStatus.SUCCEEDED : ProjectCollectionStatus.FAILED;
    }

    public void queueExternalCollection() {
        ProjectCollectionStatus status = getExternalCollectionStatus();
        if (status == ProjectCollectionStatus.NOT_STARTED
                || status == ProjectCollectionStatus.PARTIAL_FAILED
                || status == ProjectCollectionStatus.FAILED) {
            externalCollectionStatus = ProjectCollectionStatus.PENDING;
        }
    }

    public void startExternalCollection() {
        ProjectCollectionStatus status = getExternalCollectionStatus();
        if (status == ProjectCollectionStatus.PENDING
                || status == ProjectCollectionStatus.PARTIAL_FAILED
                || status == ProjectCollectionStatus.FAILED) {
            externalCollectionStatus = ProjectCollectionStatus.RUNNING;
        }
    }

    public void skipExternalCollection() {
        externalCollectionStatus = ProjectCollectionStatus.NOT_REQUIRED;
    }

    public void finishExternalCollection(ProjectCollectionStatus terminalStatus) {
        if (terminalStatus != ProjectCollectionStatus.SUCCEEDED
                && terminalStatus != ProjectCollectionStatus.PARTIAL_FAILED
                && terminalStatus != ProjectCollectionStatus.FAILED) {
            throw new IllegalArgumentException("external collection status must be terminal");
        }
        externalCollectionStatus = terminalStatus;
    }

    public boolean openPeerEvaluation() {
        if (getPeerEvaluationStatus() != PeerEvaluationStatus.PENDING || isCompleted()) {
            return false;
        }
        peerEvaluationStatus = PeerEvaluationStatus.OPEN;
        return true;
    }

    public void closePeerEvaluation() {
        peerEvaluationStatus = PeerEvaluationStatus.CLOSED;
    }

    private static void validateInviteTokenValues(String inviteTokenHash, String inviteTokenEncrypted) {
        if (inviteTokenHash == null || inviteTokenHash.isBlank()
                || inviteTokenEncrypted == null || inviteTokenEncrypted.isBlank()) {
            throw new IllegalArgumentException("invite token values must not be blank");
        }
    }

    /**
     * 평가 대기 상태 판정. 기준은 마감일 하나뿐이다 — 업무 진행률은 보지 않는다.
     * <p>
     * 마감일 "당일"부터 열린다(경계 포함). 마감일 당일로 프로젝트를 만든 팀도 그날 바로 평가할 수 있어야 하고,
     * 완료 전환 타임아웃이 endDay+7일이라 당일부터 열어야 7일 창이 온전히 확보된다.
     * 리포트가 발행되면(COMPLETED) 더 이상 평가 대기가 아니다.
     */
    public boolean isEvaluatingState(LocalDate today) {
        return !isCompleted() && getPeerEvaluationStatus() == PeerEvaluationStatus.OPEN;
    }

    /** 평가가 닫히는 날. 이 날짜부터는 미제출자가 있어도 더 기다리지 않는다. */
    public LocalDate evaluationDeadline() {
        return this.endDay.plusDays(EVALUATION_TIMEOUT_DAYS);
    }

    /** 유예 기간이 끝나 평가를 닫아도 되는지. */
    public boolean isEvaluationClosed(LocalDate today) {
        return !today.isBefore(evaluationDeadline());
    }

    /**
     * "오늘 기준으로 이미 평가가 닫힌 프로젝트"를 endDay 컬럼만으로 거르기 위한 상한값.
     * {@code endDay <= latestEndDayWithClosedEvaluation(today)} 는 {@link #isEvaluationClosed}
     * 와 같은 조건이며, 엔티티를 로딩하지 않고 쿼리에서 판정할 때 쓴다.
     */
    public static LocalDate latestEndDayWithClosedEvaluation(LocalDate today) {
        return today.minusDays(EVALUATION_TIMEOUT_DAYS);
    }
}
