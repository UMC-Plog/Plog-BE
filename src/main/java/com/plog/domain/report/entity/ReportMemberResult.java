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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 세부 컬럼(점수 5개 decimal, strengths, improvements, content, raw_response 등)은
// 팀 확정 후 추가 예정. 현재 PK/FK만 세팅.
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
}
