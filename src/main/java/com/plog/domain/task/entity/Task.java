package com.plog.domain.task.entity;

import com.plog.domain.project.entity.ProjectMember;
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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@Table(name = "tasks")
public class Task extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long id;

    // 담당자 미배정 카드는 기획상 없음 → NOT NULL 유지, project는 projectMember.project로 유도
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    // TODO(팀 확인): ERD는 NULL 허용이지만 제목 없는 업무카드가 가능한지 확인 필요.
    //  불가능하다면 nullable = false로 변경 권장.
    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private TaskCategory category;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status")
    private TaskStatus cardStatus;

    // 마감 준수율 계산용: DONE으로 바뀌는 시점에 서비스 로직에서 세팅,
    // DONE에서 되돌리면 null로 초기화
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // Project를 직접 참조하지 않고 ProjectMember를 통해서만 프로젝트와 연결한다.
    // (project_id 컬럼 없음 — projectId는 URL 검증용으로만 사용되고 저장되지 않음)
    public static Task create(ProjectMember projectMember, String title, TaskCategory category,
                              TaskStatus cardStatus, LocalDate endDate) {
        return Task.builder()
                .projectMember(projectMember)
                .title(title)
                .category(category)
                .cardStatus(cardStatus)
                .endDate(endDate)
                .completedAt(
                        cardStatus == TaskStatus.DONE
                                ? TimeUtil.nowUtc()
                                : null
                )
                .build();
    }

    // 부분 수정(PATCH)에서 값이 들어온 필드만 개별 호출한다.
    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeAssignee(ProjectMember projectMember) {
        this.projectMember = projectMember;
    }

    public void changeCategory(TaskCategory category) {
        this.category = category;
    }

    public void changeEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
