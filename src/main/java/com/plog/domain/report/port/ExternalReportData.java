package com.plog.domain.report.port;

import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.SourceDomain;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 상완 담당 구간(외부 연동 집계 + 신뢰도 판정)의 산출물. 멤버 1명분이다.
 * <p>
 * 외부 연동은 1~3단계(정제/분류/연결)를 건너뛴다 — rawActivityType 자체가 이미 세분류이기 때문이다.
 *
 * @param externalToolConnected 하나라도 연동됐는지. false 면 점수 가중치가 비례 재분배되고,
 *                              LLM 프롬프트도 단정적 평가를 피하도록 지시가 바뀐다
 * @param activityCountByDomain 외부 도메인별 활동 건수. 미연동 도메인은 키를 생략한다
 * @param competencyEvidence    역량축 근거. 송민 쪽 내부 활동 근거와 병합되어 LLM 에 전달된다
 * @param externalScore         4-외부 점수 0~100. 미연동이거나 미계산이면 null
 * @param reliabilityTier       분석 신뢰도 등급. P0 이 가장 신뢰도 높은 근거(Plog 내부 업무카드·산출물)다
 * @param cautionText           분석 한계 안내 문구. 없으면 null.
 *                              값이 있으면 LLM 이 이를 반영해 단정적 표현을 피한다
 */
public record ExternalReportData(
        boolean externalToolConnected,
        Map<SourceDomain, Long> activityCountByDomain,
        Map<CompetencyCategory, List<String>> competencyEvidence,
        BigDecimal externalScore,
        ReliabilityTier reliabilityTier,
        String cautionText
) {
    public ExternalReportData {
        activityCountByDomain = activityCountByDomain == null ? Map.of() : Map.copyOf(activityCountByDomain);
        competencyEvidence = competencyEvidence == null ? Map.of() : Map.copyOf(competencyEvidence);
    }

    /**
     * 외부 도구를 하나도 연동하지 않은 팀의 기본값.
     * 신뢰도는 Plog 내부 근거만 있는 상태이므로 P0 이고, 안내 문구를 함께 실어 보낸다.
     */
    public static ExternalReportData notConnected() {
        return new ExternalReportData(
                false,
                Map.of(),
                Map.of(),
                null,
                ReliabilityTier.P0,
                "외부 도구가 연동되지 않아 Plog 내부 활동만으로 분석했습니다."
        );
    }
}
