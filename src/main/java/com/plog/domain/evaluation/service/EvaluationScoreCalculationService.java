package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.repository.TaskRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서희 담당 리포트 입력값 계산기: Peer 보정과 자기피드백-실제로그 일치도. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationScoreCalculationService {
    private static final double PEER_SCORE_CENTER = 50.0;
    private static final double PEER_SCORE_SPREAD = 15.0;
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final PeerEvaluationRepository peerEvaluationRepository;
    private final SelfFeedbackRepository selfFeedbackRepository;
    private final TaskRepository taskRepository;
    private final ReportActivityLogRepository activityLogRepository;

    /** 멤버별 4개 항목 평균을 0~100으로 정규화한 뒤 팀 평균/표준편차로 Z-score 보정한다. */
    public Map<Long, PeerScoreResult> calculatePeerScores(Long projectId) {
        List<PeerEvaluation> evaluations = peerEvaluationRepository.findAllByEvaluateeProjectId(projectId);
        Map<Long, List<PeerEvaluation>> byEvaluatee = evaluations.stream()
                .collect(Collectors.groupingBy(evaluation -> evaluation.getEvaluatee().getId()));

        Map<Long, Double> rawScores = new HashMap<>();
        Map<Long, Map<String, Double>> categoryScores = new HashMap<>();
        byEvaluatee.forEach((memberId, memberEvaluations) -> {
            Map<String, Double> categories = categoryAverages(memberEvaluations);
            categoryScores.put(memberId, categories);
            rawScores.put(memberId, categories.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 20.0);
        });

        double teamMean = rawScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = rawScores.values().stream()
                .mapToDouble(score -> Math.pow(score - teamMean, 2))
                .average().orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        Map<Long, PeerScoreResult> results = new HashMap<>();
        rawScores.forEach((memberId, rawScore) -> {
            double adjusted = standardDeviation == 0.0
                    ? rawScore
                    : clamp(PEER_SCORE_CENTER + PEER_SCORE_SPREAD * (rawScore - teamMean) / standardDeviation);
            List<PeerEvaluation> memberEvaluations = byEvaluatee.get(memberId);
            double average = memberEvaluations.stream()
                    .mapToDouble(this::averageRawScore)
                    .average().orElse(0.0);
            results.put(memberId, new PeerScoreResult(
                    BigDecimal.valueOf(adjusted).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP),
                    categoryScores.get(memberId)));
        });
        return results;
    }

    public PeerScoreResult calculatePeerScore(Long projectId, Long projectMemberId) {
        return calculatePeerScores(projectId).getOrDefault(
                projectMemberId, new PeerScoreResult(BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyMap()));
    }

    /** 자기 피드백에서 담당 업무명을 찾고, 해당 업무에 연결된 활동 로그가 있는지 판정한다. */
    public SelfFeedbackMatchSummary calculateSelfFeedbackMatch(Long projectMemberId) {
        SelfFeedback feedback = selfFeedbackRepository.findByProjectMemberId(projectMemberId).orElse(null);
        if (feedback == null || feedback.getContent() == null || feedback.getContent().isBlank()) {
            return SelfFeedbackMatchSummary.empty();
        }

        List<Task> tasks = taskRepository.findAllByProjectMember_IdOrderByCreatedAtAsc(projectMemberId);
        List<ReportActivityLog> logs = activityLogRepository.findByProjectMember_Id(projectMemberId);
        int matched = 0;
        int unmatched = 0;
        int uncertain = 0;
        String normalizedFeedback = normalize(feedback.getContent());
        for (Task task : tasks) {
            if (task.getTitle() == null || task.getTitle().isBlank()) {
                continue;
            }
            String normalizedTitle = normalize(task.getTitle());
            if (normalizedTitle.isBlank()) {
                continue;
            }
            Mention mention = findMention(normalizedFeedback, normalizedTitle);
            if (mention == Mention.NONE) {
                continue;
            }
            if (mention == Mention.UNCERTAIN) {
                uncertain++;
                continue;
            }
            boolean hasActivity = logs.stream()
                    .anyMatch(log -> log.getLinkedTask() != null
                            && Objects.equals(log.getLinkedTask().getId(), task.getId()));
            if (hasActivity) {
                matched++;
            } else {
                unmatched++;
            }
        }
        int total = matched + unmatched + uncertain;
        double ratio = total == 0 ? 0.0 : (double) matched / total;
        return new SelfFeedbackMatchSummary(matched, unmatched, uncertain,
                BigDecimal.valueOf(ratio).setScale(4, RoundingMode.HALF_UP));
    }

    private Map<String, Double> categoryAverages(List<PeerEvaluation> evaluations) {
        Map<String, Double> values = new HashMap<>();
        values.put("협업태도", evaluations.stream().mapToInt(PeerEvaluation::getCollaborationScore).average().orElse(0.0));
        values.put("리더십", evaluations.stream().mapToInt(PeerEvaluation::getInitiativeScore).average().orElse(0.0));
        values.put("소통능력", evaluations.stream().mapToInt(PeerEvaluation::getCommunicationScore).average().orElse(0.0));
        values.put("산출물기여", evaluations.stream().mapToInt(PeerEvaluation::getOutputScore).average().orElse(0.0));
        return values;
    }

    private double averageRawScore(PeerEvaluation evaluation) {
        return (evaluation.getCollaborationScore() + evaluation.getInitiativeScore()
                + evaluation.getCommunicationScore() + evaluation.getOutputScore()) / 4.0;
    }

    private Mention findMention(String feedback, String title) {
        if (feedback.contains(title)) {
            return Mention.EXACT;
        }
        List<String> titleTokens = new ArrayList<>(List.of(title.split(" ")));
        if (titleTokens.size() < 2) {
            return Mention.NONE;
        }
        long matchedTokens = titleTokens.stream().filter(feedback::contains).count();
        return matchedTokens * 2 >= titleTokens.size() ? Mention.UNCERTAIN : Mention.NONE;
    }

    private String normalize(String text) {
        return NON_WORD.matcher(text.toLowerCase()).replaceAll(" ").trim().replaceAll(" +", " ");
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }

    private enum Mention { NONE, EXACT, UNCERTAIN }

    public record PeerScoreResult(
            BigDecimal score,
            BigDecimal average,
            Map<String, Double> categoryScores
    ) {
    }

    public record SelfFeedbackMatchSummary(
            int matchedCount,
            int unmatchedCount,
            int uncertainCount,
            BigDecimal matchRatio
    ) {
        public static SelfFeedbackMatchSummary empty() {
            return new SelfFeedbackMatchSummary(0, 0, 0, BigDecimal.ZERO.setScale(4));
        }
    }
}
