package com.plog.domain.evaluation.entity;

import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// TODO(팀 확인): 평가 1건당 키워드/피드백 1세트(1:1)이므로 peer_evaluations에
//  keywords/feedback 컬럼으로 흡수하는 것을 권장했으나, ERD에 별도 테이블로 유지되어 있어 그대로 구현.
//  1:1 관계이므로 @OneToOne + peer_id 유니크로 세팅.
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "peer_review_keyword")
public class PeerReviewKeyword extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "peer_id", nullable = false, unique = true)
    private PeerEvaluation peerEvaluation;

    // 선택된 키워드 배열 (예: ["리더십", "문제 해결"])
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keyword", columnDefinition = "jsonb")
    private String keyword;

    // 와이어프레임 기준 200자 제한
    @Column(name = "feedback", length = 200)
    private String feedback;
}
