package com.plog.domain.report.service;

import com.plog.domain.report.entity.ReliabilityTier;
import java.math.BigDecimal;

public record MemberScoreInput(
        BigDecimal internalScore,
        BigDecimal externalScore,
        BigDecimal peerScore,
        BigDecimal selfFeedbackScore,
        boolean externalToolConnected,
        ReliabilityTier reliabilityTier,
        String cautionText
) {
}
