package com.plog.domain.report.llm;

import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.InternalReportData;
import com.plog.domain.report.port.PeerEvaluationSummary;
import com.plog.domain.report.port.SelfFeedbackMatchSummary;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * LLM 에 넘길 멤버 1명분 입력. 세 포트의 결과를 여기서 하나로 합친다.
 * <p>
 * <b>실명·이메일·프로젝트명을 담지 않는다.</b> 무료 티어는 입력이 모델 개선에 쓰일 수 있어서,
 * 개인을 특정할 수 있는 값은 애초에 보내지 않는다. 대신 프로젝트 유형과 활동 요약만 넘기고,
 * 생성된 문장에 이름이 필요하면 서버가 나중에 붙인다.
 * <p>
 * 원본 로그도 넘기지 않는다 — 이미 요약된 근거 문자열만 담는다.
 */
public record MemberLlmInput(
        ProjectType projectType,
        int teamSize,
        double completionRate,
        double deadlineComplianceRate,
        int totalTaskCount,
        int completedTaskCount,
        List<String> taskTitles,
        List<String> deliverables,
        Map<String, Integer> activityTypeSummary,
        Map<String, Long> externalActivityCountByDomain,
        Map<String, List<String>> competencyEvidence,
        BigDecimal finalScore,
        Map<String, BigDecimal> peerCategoryScores,
        BigDecimal peerAverage,
        int peerEvaluatorCount,
        List<String> peerKeywords,
        boolean selfFeedbackSubmitted,
        BigDecimal selfFeedbackMatchRatio,
        boolean externalToolConnected,
        ReliabilityTier reliabilityTier,
        String cautionText
) {

    /**
     * 세 포트 결과 + 조립된 최종 점수로 LLM 입력을 만든다.
     * 역량 근거는 내부(송민)와 외부(상완)를 역량축으로 병합한다 — 같은 역량에 두 출처의 근거가
     * 함께 붙어야 LLM 이 "채팅에서도 GitHub 에서도 확인됨" 같은 서술을 할 수 있다.
     */
    public static MemberLlmInput of(
            ProjectType projectType,
            int teamSize,
            InternalReportData internal,
            ExternalReportData external,
            PeerEvaluationSummary peer,
            SelfFeedbackMatchSummary self,
            BigDecimal finalScore
    ) {
        return new MemberLlmInput(
                projectType,
                teamSize,
                internal.completionRate(),
                internal.deadlineComplianceRate(),
                internal.totalTaskCount(),
                internal.completedTaskCount(),
                internal.taskCardSummary().stream().map(task -> task.title()).toList(),
                internal.deliverableAttachmentInfo(),
                toStringKeyed(internal.activityTypeSummary()),
                toStringKeyed(external.activityCountByDomain()),
                mergeEvidence(internal.competencyEvidence(), external.competencyEvidence()),
                finalScore,
                toStringKeyed(peer.categoryScores()),
                peer.average(),
                peer.evaluatorCount(),
                peer.keywords(),
                self.submitted(),
                self.matchRatio(),
                external.externalToolConnected(),
                external.reliabilityTier(),
                external.cautionText()
        );
    }

    private static <K extends Enum<K>, V> Map<String, V> toStringKeyed(Map<K, V> source) {
        Map<String, V> result = new TreeMap<>();
        source.forEach((key, value) -> result.put(key.name(), value));
        return result;
    }

    private static Map<String, List<String>> mergeEvidence(
            Map<CompetencyCategory, List<String>> internal,
            Map<CompetencyCategory, List<String>> external
    ) {
        Map<String, List<String>> merged = new LinkedHashMap<>();
        for (CompetencyCategory category : CompetencyCategory.values()) {
            List<String> combined = java.util.stream.Stream
                    .concat(
                            internal.getOrDefault(category, List.of()).stream(),
                            external.getOrDefault(category, List.of()).stream()
                    )
                    .distinct()
                    .toList();
            if (!combined.isEmpty()) {
                merged.put(category.name(), combined);
            }
        }
        return merged;
    }
}
