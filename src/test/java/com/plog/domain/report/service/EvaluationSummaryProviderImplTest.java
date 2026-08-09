package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.evaluation.service.EvaluationScoreCalculationService;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluationSummaryProviderImplTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 7L;

    @Mock private PeerEvaluationRepository peerEvaluationRepository;
    @Mock private SelfFeedbackRepository selfFeedbackRepository;
    @Mock private EvaluationScoreCalculationService calculationService;

    @InjectMocks private EvaluationSummaryProviderImpl provider;

    // 받은 평가가 없는 멤버는 예외가 아니라 none() — 참여 저조한 멤버도 리포트에 나와야 한다.
    @Test
    void peer_받은_평가가_없으면_none을_돌려준다() {
        when(peerEvaluationRepository.findAllByEvaluateeIdOrderByCreatedAtAscIdAsc(MEMBER_ID)).thenReturn(List.of());

        PeerEvaluationSummary summary = provider.peer(PROJECT_ID, MEMBER_ID);

        assertThat(summary.hasEvaluation()).isFalse();
        assertThat(summary.average()).isNull();
        assertThat(summary.normalizedScore()).isEqualByComparingTo("0.00");
        assertThat(summary.categoryScores()).isEmpty();
        assertThat(summary.keywords()).isEmpty();
    }

    // LEADERSHIP 라벨이 initiativeScore(주도성) 에 매핑되는지, 5점 척도와 100점 척도가 분리되는지 고정한다.
    @Test
    void peer_역량점수와_키워드와_정규화점수를_집계한다() {
        PeerEvaluation first = PeerEvaluation.builder()
                .collaborationScore(4).initiativeScore(5).communicationScore(4).outputScore(4)
                .keywords(List.of("리더십", "책임감"))
                .build();
        PeerEvaluation second = PeerEvaluation.builder()
                .collaborationScore(5).initiativeScore(3).communicationScore(4).outputScore(5)
                .keywords(List.of("리더십", "꼼꼼함"))
                .build();
        when(peerEvaluationRepository.findAllByEvaluateeIdOrderByCreatedAtAscIdAsc(MEMBER_ID)).thenReturn(List.of(first, second));
        when(calculationService.calculatePeerScore(PROJECT_ID, MEMBER_ID))
                .thenReturn(new EvaluationScoreCalculationService.PeerScoreResult(
                        new BigDecimal("80.00"), new BigDecimal("4.25"), Map.of()));

        PeerEvaluationSummary summary = provider.peer(PROJECT_ID, MEMBER_ID);

        assertThat(summary.evaluatorCount()).isEqualTo(2);
        // 주도성(초기값 5,3) 평균 4.0 이 화면 라벨 LEADERSHIP 으로 들어간다.
        assertThat(summary.categoryScores().get(CompetencyCategory.LEADERSHIP)).isEqualByComparingTo("4.00");
        assertThat(summary.categoryScores().get(CompetencyCategory.COLLABORATION)).isEqualByComparingTo("4.50");
        // 종합 평균은 (4.5+4.0+4.0+4.5)/4 = 4.25, 5점 척도.
        assertThat(summary.average()).isEqualByComparingTo("4.25");
        // 100점 척도(Z-score)는 계산 서비스 값을 그대로 쓴다.
        assertThat(summary.normalizedScore()).isEqualByComparingTo("80.00");
        // 키워드는 최초 등장 순서로 중복 없이.
        assertThat(summary.keywords()).containsExactly("리더십", "책임감", "꼼꼼함");
    }

    @Test
    void self_피드백이_없으면_notSubmitted를_돌려준다() {
        when(selfFeedbackRepository.findByProjectMemberId(MEMBER_ID)).thenReturn(Optional.empty());

        SelfFeedbackMatchSummary summary = provider.self(PROJECT_ID, MEMBER_ID);

        assertThat(summary.submitted()).isFalse();
        assertThat(summary.matchRatio()).isNull();
        assertThat(summary.normalizedScore()).isEqualByComparingTo("0.00");
    }

    @Test
    void self_제출했으면_일치도를_0에서_100으로_정규화한다() {
        when(selfFeedbackRepository.findByProjectMemberId(MEMBER_ID))
                .thenReturn(Optional.of(SelfFeedback.builder().content("담당 업무를 성실히 수행했습니다").build()));
        when(calculationService.calculateSelfFeedbackMatch(MEMBER_ID))
                .thenReturn(new EvaluationScoreCalculationService.SelfFeedbackMatchSummary(
                        3, 1, 0, new BigDecimal("0.7500")));

        SelfFeedbackMatchSummary summary = provider.self(PROJECT_ID, MEMBER_ID);

        assertThat(summary.submitted()).isTrue();
        assertThat(summary.matchRatio()).isEqualByComparingTo("0.7500");
        assertThat(summary.normalizedScore()).isEqualByComparingTo("75.00");
    }

    // 제출은 했지만 로그와 대조할 업무 서술이 하나도 없는 경우 — 일치 비율은 null(포트 계약).
    @Test
    void self_판정_대상이_없으면_matchRatio는_null이다() {
        when(selfFeedbackRepository.findByProjectMemberId(MEMBER_ID))
                .thenReturn(Optional.of(SelfFeedback.builder().content("소감만 적었습니다").build()));
        when(calculationService.calculateSelfFeedbackMatch(MEMBER_ID))
                .thenReturn(new EvaluationScoreCalculationService.SelfFeedbackMatchSummary(
                        0, 0, 0, BigDecimal.ZERO.setScale(4)));

        SelfFeedbackMatchSummary summary = provider.self(PROJECT_ID, MEMBER_ID);

        assertThat(summary.submitted()).isTrue();
        assertThat(summary.matchRatio()).isNull();
        assertThat(summary.normalizedScore()).isEqualByComparingTo("0.00");
    }
}
