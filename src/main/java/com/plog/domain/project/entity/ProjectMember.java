package com.plog.domain.project.entity;

import com.plog.domain.user.entity.User;
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
@Table(name = "project_members", uniqueConstraints = {
        // 같은 유저가 같은 프로젝트에 중복 가입 불가
        @UniqueConstraint(
                name = ProjectMember.UNIQUE_PROJECT_MEMBER_CONSTRAINT,
                columnNames = {"user_id", "project_id"}
        )
})
public class ProjectMember extends BaseEntity {

    public static final String UNIQUE_PROJECT_MEMBER_CONSTRAINT = "uk_project_member";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_member_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // ERD는 varchar("owner? member")였으나 값이 2개로 고정되므로 enum으로 구현
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private ProjectRole role;

    // TODO(팀 확인): ERD 컬럼명이 project_status인데 "멤버의 상태(ACTIVE/EXIT)"이므로
    //  member_status가 더 명확함. 일단 ERD대로 project_status 사용.
    @Enumerated(EnumType.STRING)
    @Column(name = "project_status")
    private MemberStatus status;

    @Column(name = "an_nickname")
    private String anNickname;

    public void reactivateAsMember() {
        this.role = ProjectRole.MEMBER;
        this.status = MemberStatus.ACTIVE;
    }

    public void leave() {
        this.status = MemberStatus.EXIT;
    }

    public void assignRole(ProjectRole role) {
        this.role = role;
    }

    public void transferOwnershipTo(ProjectMember targetMember) {
        this.role = ProjectRole.MEMBER;
        targetMember.assignRole(ProjectRole.OWNER);
    }
}
