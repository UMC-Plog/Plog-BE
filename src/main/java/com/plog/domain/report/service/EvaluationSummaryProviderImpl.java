package com.plog.domain.report.service;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.evaluation.service.EvaluationScoreCalculationService;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.port.EvaluationSummaryProvider;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EvaluationSummaryProvider} 실제 구현 (서희 담당: Peer 평가 집계 · 자기 피드백 일치도).
 * <p>
 * {@code @Service} 로 등록되면 {@code ReportPortFallbackConfig} 의
 * {@code fakeEvaluationSummaryProvider} 가 {@code @ConditionalOnMissingBean} 으로 자동으로 물러난다.
 * <p>
 * 두 축의 척도를 섞지 않도록 주의한다({@link PeerEvaluationSummary} 참고):
 * <ul>
 *   <li>{@code average}/{@code categoryScores} 는 화면 표기와 같은 <b>5점 척도</b>
 *       ({@code PeerEvaluation} 원본 컬럼 척도).</li>
 *   <li>{@code normalizedScore} 만 점수 조립용 <b>0~100 척도</b>
 *       (팀 평균/표준편차로 보정한 Z-score, {@link EvaluationScoreCalculationService}).</li>
 * </ul>
 * 데이터가 없는 멤버는 예외 대신 {@link PeerEvaluationSummary#none()} /
 * {@link SelfFeedbackMatchSummary#notSubmitted()} 를 돌려준다 — 참여가 저조한 멤버도 리포트에 나와야 한다.
 */
@Service
@RequiredArgsConstructor
public class EvaluationSummaryProviderImpl implements EvaluationSummaryProvider {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PeerEvaluationRepository peerEvaluationRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;
    private final EvaluationScoreCalculationService calculationService;

    @Override
    @Transactional(readOnly = true)
    public PeerEvaluationSummary peer(Long projectId, Long projectMemberId) {
        List<PeerEvaluation> received =
                peerEvaluationRepository.findAllByEvaluateeIdOrderByCreatedAtAscIdAsc(projectMemberId);
        if (received.isEmpty()) {
            // 아직 아무에게도 평가받지 못한 멤버 — 타임아웃 발행 경로에서 실제로 발생한다.
            return PeerEvaluationSummary.none();
        }

        // 5점 척도(화면 표기). 컬럼명과 화면 라벨이 다른 건 LEADERSHIP 하나뿐이다:
        // PeerEvaluation.initiativeScore(주도성) 가 화면의 "리더십"이다.
        double collaboration = rawAverage(received, PeerEvaluation::getCollaborationScore);
        double leadership = rawAverage(received, PeerEvaluation::getInitiativeScore);
        double communication = rawAverage(received, PeerEvaluation::getCommunicationScore);
        double output = rawAverage(received, PeerEvaluation::getOutputScore);

        Map<CompetencyCategory, BigDecimal> categoryScores = new EnumMap<>(CompetencyCategory.class);
        categoryScores.put(CompetencyCategory.COLLABORATION, scale2(collaboration));
        categoryScores.put(CompetencyCategory.LEADERSHIP, scale2(leadership));
        categoryScores.put(CompetencyCategory.COMMUNICATION, scale2(communication));
        categoryScores.put(CompetencyCategory.OUTPUT, scale2(output));

        BigDecimal average = scale2((collaboration + leadership + communication + output) / 4.0);
        List<String> keywords = distinctKeywords(received);

        // 0~100 정규화 점수는 팀 전체가 있어야 계산되므로(Z-score) 계산 서비스에 위임한다.
        BigDecimal normalizedScore = calculationService
                .calculatePeerScore(projectId, projectMemberId)
                .score();

        // uk_peer_evaluator_evaluatee 로 평가자당 1건이 보장되므로 행 수 == 평가자 수.
        return new PeerEvaluationSummary(average, categoryScores, received.size(), keywords, normalizedScore);
    }

    @Override
    @Transactional(readOnly = true)
    public SelfFeedbackMatchSummary self(Long projectId, Long projectMemberId) {
        SelfFeedback feedback = selfFeedbackRepository.findByProjectMemberId(projectMemberId).orElse(null);
        if (feedback == null || feedback.getContent() == null || feedback.getContent().isBlank()) {
            // 자기 피드백은 완료 필수 조건이 아니다 — 미제출이면 예외 대신 notSubmitted().
            return SelfFeedbackMatchSummary.notSubmitted();
        }

        EvaluationScoreCalculationService.SelfFeedbackMatchSummary match =
                calculationService.calculateSelfFeedbackMatch(projectMemberId);

        int judgeable = match.matchedCount() + match.unmatchedCount() + match.uncertainCount();
        // 판정 대상(로그와 대조할 업무 서술)이 하나도 없으면 일치 비율은 null 이다(포트 계약).
        BigDecimal matchRatio = judgeable == 0 ? null : match.matchRatio();
        BigDecimal normalizedScore = judgeable == 0
                ? BigDecimal.ZERO.setScale(2)
                : match.matchRatio().multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);

        return new SelfFeedbackMatchSummary(
                true,
                match.matchedCount(),
                match.unmatchedCount(),
                match.uncertainCount(),
                matchRatio,
                normalizedScore
        );
    }

    private double rawAverage(List<PeerEvaluation> evaluations, ToIntFunction<PeerEvaluation> field) {
        return evaluations.stream().mapToInt(field).average().orElse(0.0);
    }

    private BigDecimal scale2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** 평가자들이 고른 키워드를 최초 등장 순서로 중복 없이 모은다(태그 칩 노출용). */
    private List<String> distinctKeywords(List<PeerEvaluation> evaluations) {
        Set<String> keywords = new LinkedHashSet<>();
        for (PeerEvaluation evaluation : evaluations) {
            if (evaluation.getKeywords() == null) {
                continue;
            }
            for (String keyword : evaluation.getKeywords()) {
                if (keyword != null && !keyword.isBlank()) {
                    keywords.add(keyword.trim());
                }
            }
        }
        return List.copyOf(keywords);
    }
}
