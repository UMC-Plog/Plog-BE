package com.plog.domain.integration.entity;

import com.plog.domain.project.entity.ProjectMember;
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
@Table(name = "external_connection", uniqueConstraints = {
        // 멤버당 앱(GITHUB/FIGMA/NOTION)별 연결 1건
        @UniqueConstraint(name = "uk_connection_member_type", columnNames = {"project_member_id", "link_type"})
})
public class ExternalConnection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    private LinkType linkType;

    // NULL = 미연결. is_linked boolean은 이 컬럼에서 유도 가능하므로 두지 않음
    @Column(name = "external_account_id")
    private String externalAccountId;

    // TODO(보안): 평문 저장 금지. 저장 전 암호화(AES) 또는 JPA AttributeConverter 적용 필요
    @Column(name = "access_token", length = 512)
    private String accessToken;

    public boolean isLinked() {
        return externalAccountId != null;
    }
}
