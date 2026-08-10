package com.plog.domain.report.service;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 멤버 점수가 모두 저장된 뒤 팀 내부 상대 지표와 팀 KPI를 확정한다. */
@Service
@RequiredArgsConstructor
public class ReportTeamMetricService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final ReportMemberResultRepository resultRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public Map<Long, MemberAnalysis> calculateAndApply(Long reportId, int expectedMemberCount) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        List<ReportMemberResult> results = resultRepository
                .findAllByReportIdOrderByProjectMemberIdAsc(reportId);
        boolean completePopulation = results.size() == expectedMemberCount;

        report.applyTeamRates(
                completePopulation ? average(results.stream().map(ReportMemberResult::getCompletionRate).toList()) : null,
                completePopulation ? average(results.stream().map(ReportMemberResult::getDeadlineComplianceRate).toList()) : null);

        Map<Long, BigDecimal> contribution = completePopulation
                ? contributionRates(results) : Map.of();
        Map<Long, RelativeMetric> peerMetrics = completePopulation
                ? relativeMetrics(results, ReportMemberResult::getPeerAverage) : Map.of();
        Map<Long, VulnerabilityMetric> vulnerabilities = completePopulation
                ? vulnerabilities(results) : Map.of();

        for (ReportMemberResult result : results) {
            Long memberId = result.getProjectMember().getId();
            RelativeMetric peer = peerMetrics.get(memberId);
            VulnerabilityMetric vulnerability = vulnerabilities.get(memberId);
            result.applyTeamAnalysis(
                    contribution.get(memberId),
                    peer == null ? null : peer.zScore(),
                    peer == null ? null : peer.percentile(),
                    collaborationStability(result),
                    vulnerability == null ? null : vulnerability.score(),
                    vulnerability == null ? null : vulnerability.category());
        }
        Map<Long, MemberAnalysis> analysis = new HashMap<>();
        for (ReportMemberResult result : results) {
            analysis.put(result.getProjectMember().getId(), new MemberAnalysis(
                    result.getCollaborationStability(),
                    result.getVulnerability(),
                    result.getVulnerableCompetency()));
        }
        return Map.copyOf(analysis);
    }

    private Map<Long, BigDecimal> contributionRates(List<ReportMemberResult> results) {
        if (results.stream().anyMatch(result -> result.getFinalScore() == null)) {
            return Map.of();
        }
        BigDecimal sum = results.stream().map(ReportMemberResult::getFinalScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() == 0) {
            return Map.of();
        }

        List<ContributionShare> shares = new ArrayList<>();
        int assignedUnits = 0;
        for (ReportMemberResult result : results) {
            BigDecimal exactUnits = result.getFinalScore()
                    .multiply(new BigDecimal("10000"))
                    .divide(sum, 12, RoundingMode.HALF_UP);
            int floorUnits = exactUnits.setScale(0, RoundingMode.DOWN).intValueExact();
            assignedUnits += floorUnits;
            shares.add(new ContributionShare(
                    result.getProjectMember().getId(), floorUnits,
                    exactUnits.subtract(BigDecimal.valueOf(floorUnits))));
        }
        shares.sort(Comparator.comparing(ContributionShare::remainder).reversed()
                .thenComparing(ContributionShare::memberId));
        int remaining = 10_000 - assignedUnits;
        Map<Long, BigDecimal> result = new HashMap<>();
        for (int index = 0; index < shares.size(); index++) {
            ContributionShare share = shares.get(index);
            int units = share.floorUnits() + (index < remaining ? 1 : 0);
            result.put(share.memberId(), BigDecimal.valueOf(units, 2));
        }
        return result;
    }

    private BigDecimal collaborationStability(ReportMemberResult result) {
        Map<CompetencyCategory, BigDecimal> scores = result.getCompetencyScores();
        BigDecimal collaboration = scores == null ? null : scores.get(CompetencyCategory.COLLABORATION);
        BigDecimal communication = scores == null ? null : scores.get(CompetencyCategory.COMMUNICATION);
        if (result.getDeadlineComplianceRate() == null || collaboration == null || communication == null) {
            return null;
        }
        return result.getDeadlineComplianceRate().multiply(new BigDecimal("0.4"))
                .add(collaboration.multiply(new BigDecimal("20")).multiply(new BigDecimal("0.3")))
                .add(communication.multiply(new BigDecimal("20")).multiply(new BigDecimal("0.3")))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<Long, VulnerabilityMetric> vulnerabilities(List<ReportMemberResult> results) {
        Map<CompetencyCategory, Map<Long, BigDecimal>> percentiles = new EnumMap<>(CompetencyCategory.class);
        for (CompetencyCategory category : CompetencyCategory.values()) {
            percentiles.put(category, categoryPercentiles(results, category));
        }

        Map<Long, VulnerabilityMetric> answer = new HashMap<>();
        for (ReportMemberResult result : results) {
            Long memberId = result.getProjectMember().getId();
            BigDecimal lowest = null;
            CompetencyCategory weakest = null;
            for (CompetencyCategory category : CompetencyCategory.values()) {
                BigDecimal percentile = percentiles.get(category).get(memberId);
                if (percentile != null && (lowest == null || percentile.compareTo(lowest) < 0)) {
                    lowest = percentile;
                    weakest = category;
                }
            }
            if (lowest != null) {
                answer.put(memberId, new VulnerabilityMetric(
                        HUNDRED.subtract(lowest).setScale(2, RoundingMode.HALF_UP), weakest));
            }
        }
        return answer;
    }

    private Map<Long, BigDecimal> categoryPercentiles(
            List<ReportMemberResult> results, CompetencyCategory category
    ) {
        return relativeMetrics(results, result -> result.getCompetencyScores() == null
                ? null : result.getCompetencyScores().get(category)).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().percentile()));
    }

    private Map<Long, RelativeMetric> relativeMetrics(
            List<ReportMemberResult> results,
            java.util.function.Function<ReportMemberResult, BigDecimal> valueExtractor
    ) {
        List<MemberValue> values = results.stream()
                .map(result -> new MemberValue(result.getProjectMember().getId(), valueExtractor.apply(result)))
                .filter(value -> value.value() != null)
                .toList();
        if (values.size() < 2) {
            return Map.of();
        }
        double mean = values.stream().mapToDouble(value -> value.value().doubleValue()).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(value -> Math.pow(value.value().doubleValue() - mean, 2))
                .average().orElse(0.0);
        double standardDeviation = Math.sqrt(variance);
        if (standardDeviation == 0.0) {
            return Map.of();
        }
        Map<Long, RelativeMetric> metrics = new HashMap<>();
        for (MemberValue value : values) {
            double z = (value.value().doubleValue() - mean) / standardDeviation;
            metrics.put(value.memberId(), new RelativeMetric(
                    BigDecimal.valueOf(z).setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(normalCdf(z) * 100).setScale(2, RoundingMode.HALF_UP)));
        }
        return metrics;
    }

    // Abramowitz-Stegun 근사. 팀 상대 위치 표시용으로 소수 둘째 자리보다 충분히 정밀하다.
    private double normalCdf(double z) {
        double absolute = Math.abs(z);
        double t = 1.0 / (1.0 + 0.2316419 * absolute);
        double density = 0.3989422804014327 * Math.exp(-absolute * absolute / 2.0);
        double probability = 1.0 - density * t * (0.319381530
                + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        return z >= 0 ? probability : 1.0 - probability;
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> available = values.stream().filter(java.util.Objects::nonNull).toList();
        if (available.isEmpty()) {
            return null;
        }
        return available.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(available.size()), 2, RoundingMode.HALF_UP);
    }

    private record ContributionShare(Long memberId, int floorUnits, BigDecimal remainder) { }
    private record MemberValue(Long memberId, BigDecimal value) { }
    private record RelativeMetric(BigDecimal zScore, BigDecimal percentile) { }
    private record VulnerabilityMetric(BigDecimal score, CompetencyCategory category) { }

    /** LLM에는 서버에서 확정된 값만 전달하고, 모델이 상대 지표를 다시 계산하지 않게 한다. */
    public record MemberAnalysis(
            BigDecimal collaborationStability,
            BigDecimal vulnerability,
            CompetencyCategory vulnerableCompetency
    ) { }
}
