package com.plog.domain.report.service;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportMemberScoreService {
    private static final BigDecimal INTERNAL_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal EXTERNAL_WEIGHT = new BigDecimal("0.15");
    private static final BigDecimal PEER_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal SELF_WEIGHT = new BigDecimal("0.15");
    private static final BigDecimal NON_EXTERNAL_WEIGHT = new BigDecimal("0.85");
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = new BigDecimal("100");

    private final ReportMemberResultRepository resultRepository;

    @Transactional
    public ReportMemberResult calculateAndSave(Report report, ProjectMember member, MemberScoreInput input) {
        if (report == null || report.getId() == null || member == null || member.getId() == null || input == null) {
            throw new IllegalArgumentException("report, member and score input must be persisted and non-null");
        }
        if (input.reliabilityTier() == null) {
            throw new IllegalArgumentException("reliabilityTier must not be null");
        }

        BigDecimal internal = normalizeRequired(input.internalScore(), "internalScore");
        BigDecimal peer = normalizeRequired(input.peerScore(), "peerScore");
        BigDecimal self = normalizeRequired(input.selfFeedbackScore(), "selfFeedbackScore");
        BigDecimal external = input.externalToolConnected()
                ? normalizeRequired(input.externalScore(), "externalScore")
                : normalizeOptional(input.externalScore(), "externalScore");

        BigDecimal weighted = internal.multiply(INTERNAL_WEIGHT)
                .add(peer.multiply(PEER_WEIGHT))
                .add(self.multiply(SELF_WEIGHT));
        if (input.externalToolConnected()) {
            weighted = weighted.add(external.multiply(EXTERNAL_WEIGHT));
        } else {
            weighted = weighted.divide(NON_EXTERNAL_WEIGHT, 8, RoundingMode.HALF_UP);
            external = null;
        }
        BigDecimal finalScore = weighted.setScale(2, RoundingMode.HALF_UP);

        ReportMemberResult result = resultRepository
                .findByReportIdAndProjectMemberId(report.getId(), member.getId())
                .orElseGet(() -> ReportMemberResult.create(report, member));
        result.applyScores(internal, external, peer, self, finalScore,
                input.externalToolConnected(), input.reliabilityTier(), input.cautionText());
        resultRepository.save(result);
        return result;
    }

    private BigDecimal normalizeRequired(BigDecimal score, String field) {
        if (score == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return normalize(score, field);
    }

    private BigDecimal normalizeOptional(BigDecimal score, String field) {
        return score == null ? null : normalize(score, field);
    }

    private BigDecimal normalize(BigDecimal score, String field) {
        if (score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
