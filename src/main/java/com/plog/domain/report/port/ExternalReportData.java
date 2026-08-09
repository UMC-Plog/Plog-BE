package com.plog.domain.report.port;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.SourceDomain;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 상완 담당 구간(외부 연동 집계 + 신뢰도 판정)의 산출물. 멤버 1명분이다.
 * <p>
 * 외부 연동은 1~3단계(정제/분류/연결)를 건너뛴다 — rawActivityType 자체가 이미 세분류이기 때문이다.
 *
 * @param externalToolConnected 이 멤버에게 매핑되어 리포트에 사용할 수 있는 외부 활동이 있는지.
 *                              프로젝트에 도구만 연동되고 멤버 계정이 미매핑이면 false이며,
 *                              점수 가중치가 비례 재분배되고 LLM도 단정적 평가를 피한다
 * @param externalScoreAvailable 외부 점수를 최종 점수에 반영할 수 있는지. false 면 externalScore 는 null 이다
 * @param activityCountByDomain  외부 도메인별 활동 건수. 미연동 도메인은 키를 생략한다
 * @param competencyActivityCount 역량별 외부 활동 건수. 모든 역량 키를 0 기본값으로 포함한다
 * @param competencyEvidence     역량축 근거. 송민 쪽 내부 활동 근거와 병합되어 LLM 에 전달된다
 * @param externalScore          4-외부 점수 0~100. 미연동이거나 미계산이면 null
 * @param reliabilityTier       분석 신뢰도 등급. P0 이 가장 신뢰도 높은 근거(Plog 내부 업무카드·산출물)다
 * @param cautionText           분석 한계 안내 문구. 없으면 null.
 *                              값이 있으면 LLM 이 이를 반영해 단정적 표현을 피한다
 */
public record ExternalReportData(
        boolean externalToolConnected,
        boolean externalScoreAvailable,
        Map<SourceDomain, Long> activityCountByDomain,
        Map<CompetencyCategory, Long> competencyActivityCount,
        Map<CompetencyCategory, List<String>> competencyEvidence,
        BigDecimal externalScore,
        ReliabilityTier reliabilityTier,
        String cautionText
) {
    public ExternalReportData {
        validateScoreContract(externalToolConnected, externalScoreAvailable, externalScore);
        activityCountByDomain = activityCountByDomain == null ? Map.of() : Map.copyOf(activityCountByDomain);
        competencyActivityCount = withAllCompetencyKeys(competencyActivityCount);
        competencyEvidence = copyEvidence(competencyEvidence);
    }

    /**
     * 외부 도구를 하나도 연동하지 않은 팀의 기본값.
     * 신뢰도는 Plog 내부 근거만 있는 상태이므로 P0 이고, 안내 문구를 함께 실어 보낸다.
     */
    public static ExternalReportData notConnected() {
        return new ExternalReportData(
                false,
                false,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                ReliabilityTier.P0,
                "외부 도구가 연동되지 않아 Plog 내부 활동만으로 분석했습니다."
        );
    }

    public static ExternalReportData notMapped() {
        return new ExternalReportData(
                false,
                false,
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                ReliabilityTier.P0,
                "외부 도구는 연동됐지만 이 멤버의 외부 계정 매핑이 없어 Plog 내부 활동만으로 분석했습니다."
        );
    }

    public static ExternalReportData connectedWithoutScore(
            Map<SourceDomain, Long> activityCountByDomain,
            Map<CompetencyCategory, Long> competencyActivityCount,
            Map<CompetencyCategory, List<String>> competencyEvidence,
            ReliabilityTier reliabilityTier,
            String cautionText
    ) {
        return new ExternalReportData(
                true,
                false,
                activityCountByDomain,
                competencyActivityCount,
                competencyEvidence,
                null,
                reliabilityTier,
                cautionText
        );
    }

    private static void validateScoreContract(
            boolean externalToolConnected,
            boolean externalScoreAvailable,
            BigDecimal externalScore
    ) {
        if (externalScoreAvailable && !externalToolConnected) {
            throw new IllegalArgumentException("externalScoreAvailable requires externalToolConnected");
        }
        if (externalScoreAvailable && externalScore == null) {
            throw new IllegalArgumentException("externalScoreAvailable requires externalScore");
        }
        if (!externalScoreAvailable && externalScore != null) {
            throw new IllegalArgumentException("externalScore must be null when unavailable");
        }
        if (externalScore != null
                && (externalScore.compareTo(BigDecimal.ZERO) < 0
                || externalScore.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("externalScore must be between 0 and 100");
        }
    }

    private static Map<CompetencyCategory, Long> withAllCompetencyKeys(
            Map<CompetencyCategory, Long> source
    ) {
        Map<CompetencyCategory, Long> result = new TreeMap<>();
        for (CompetencyCategory category : CompetencyCategory.values()) {
            result.put(category, 0L);
        }
        if (source != null) {
            source.forEach((category, count) -> result.put(category, count == null ? 0L : count));
        }
        return Map.copyOf(result);
    }

    private static Map<CompetencyCategory, List<String>> copyEvidence(
            Map<CompetencyCategory, List<String>> source
    ) {
        if (source == null) {
            return Map.of();
        }
        return Map.copyOf(source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() == null ? List.of() : List.copyOf(entry.getValue())
                )));
    }
}
