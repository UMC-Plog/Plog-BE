package com.plog.domain.report.service;

import com.plog.domain.report.entity.ReliabilityTier;
import java.math.BigDecimal;

public record MemberScoreInput(
        BigDecimal internalScore,
        BigDecimal externalScore,
        BigDecimal peerScore,
        BigDecimal selfFeedbackScore,
        boolean externalToolConnected,
        boolean externalScoreAvailable,
        ReliabilityTier reliabilityTier,
        String cautionText
) {
    public MemberScoreInput {
        if (externalScoreAvailable && !externalToolConnected) {
            throw new IllegalArgumentException("externalScoreAvailable requires externalToolConnected");
        }
        if (externalScoreAvailable && externalScore == null) {
            throw new IllegalArgumentException("externalScoreAvailable requires externalScore");
        }
        if (!externalScoreAvailable && externalScore != null) {
            throw new IllegalArgumentException("externalScore must be null when unavailable");
        }
    }
}
