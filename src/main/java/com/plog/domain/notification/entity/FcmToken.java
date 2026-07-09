package com.plog.domain.notification.entity;

import com.plog.domain.user.entity.User;
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

// TODO(팀 확인): FCM 토큰 엔티티 위치를 notification 도메인에 둠 (알림 로직이 유일한 사용처).
//  user 도메인이 더 맞다고 판단되면 이동.
@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fcm", uniqueConstraints = {
        // 토큰 = 디바이스 식별자. 같은 토큰이 여러 유저 행에 걸리면 알림 오발송 위험
        @UniqueConstraint(name = "uk_fcm_token", columnNames = "token")
})
public class FcmToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcm_id")
    private Long id;

    // user_id에는 유니크를 걸지 않음: 한 유저가 여러 기기(토큰)를 가질 수 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // TODO(구현 규칙): 재등록 시 upsert — 토큰이 이미 존재하면 user 참조를 갱신 (기기 주인 교체).
    //  soft delete 금지 — deleted_at 처리된 행이 유니크 제약을 점유해 재등록이 막힘. 물리 삭제로 처리.
    @Column(name = "token", nullable = false, length = 512)
    private String token;
}
