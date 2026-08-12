package com.plog.domain.report.entity;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "report_member_result", uniqueConstraints = {
        // 한 리포트에 같은 멤버의 결과는 1건만
        @UniqueConstraint(name = "uk_report_member", columnNames = {"report_id", "project_member_id"})
})
public class ReportMemberResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember projectMember;

    @Column(name = "internal_score", precision = 5, scale = 2)
    private BigDecimal internalScore;

    @Column(name = "external_score", precision = 5, scale = 2)
    private BigDecimal externalScore;

    @Column(name = "peer_score", precision = 5, scale = 2)
    private BigDecimal peerScore;

    @Column(name = "self_feedback_score", precision = 5, scale = 2)
    private BigDecimal selfFeedbackScore;

    @Column(name = "final_score", precision = 5, scale = 2)
    private BigDecimal finalScore;

    @Column(name = "external_tool_connected", nullable = false)
    private boolean externalToolConnected;

    @Enumerated(EnumType.STRING)
    @Column(name = "reliability_tier", length = 10)
    private ReliabilityTier reliabilityTier;

    @Column(name = "caution_text", columnDefinition = "TEXT")
    private String cautionText;

    // ── 팀 리포트 표 표시용 업무 집계. InternalReportData 조립 시점(0~1단계)에 함께 채워진다. ──
    /** 부여된 전체 업무 수. 팀 리포트 표의 "전체". */
    @Column(name = "total_task_count", nullable = false, columnDefinition = "integer default 0")
    private int totalTaskCount;

    /** 완료한 업무 수. 팀 리포트 표의 "완료". */
    @Column(name = "completed_task_count", nullable = false, columnDefinition = "integer default 0")
    private int completedTaskCount;

    /** 기한 내 완료한 업무 수. 화면 "12/13건" 표기의 앞 숫자. */
    @Column(name = "deadline_met_task_count", nullable = false, columnDefinition = "integer default 0")
    private int deadlineMetTaskCount;

    /** 마감일이 있어 준수율 분모에 포함되는 업무 수. */
    @Column(name = "deadline_target_task_count", nullable = false, columnDefinition = "integer default 0")
    private int deadlineTargetTaskCount;

    /** 업무가 없으면 null. 0~100 퍼센트 스냅샷. */
    @Column(name = "completion_rate", precision = 5, scale = 2)
    private BigDecimal completionRate;

    /** 마감일이 있는 업무가 없으면 null. 0~100 퍼센트 스냅샷. */
    @Column(name = "deadline_compliance_rate", precision = 5, scale = 2)
    private BigDecimal deadlineComplianceRate;

    @Column(name = "contribution_rate", precision = 5, scale = 2)
    private BigDecimal contributionRate;

    @Column(name = "peer_z_score", precision = 7, scale = 4)
    private BigDecimal peerZScore;

    @Column(name = "peer_percentile", precision = 5, scale = 2)
    private BigDecimal peerPercentile;

    @Column(name = "collaboration_stability", precision = 5, scale = 2)
    private BigDecimal collaborationStability;

    @Column(name = "vulnerability", precision = 5, scale = 2)
    private BigDecimal vulnerability;

    @Enumerated(EnumType.STRING)
    @Column(name = "vulnerable_competency", length = 20)
    private CompetencyCategory vulnerableCompetency;

    // ── 팀 리포트 시안의 역량점수/태그 표시용 Peer 집계. 점수 확정 시점에 함께 채워진다. ──
    // peer_score는 5점 평균을 0~100으로 환산한 종합점수용 값이고, 아래는 화면 표기용 5점 척도다.
    // 근거가 없는 멤버(none())는 null/빈 값으로 남고, 화면은 빈 섹션을 숨긴다.

    /** 종합 Peer 평균 (0.00~5.00, 5점 척도). 개인 리포트의 역량 종합 점수. */
    @Column(name = "peer_average", precision = 3, scale = 2)
    private BigDecimal peerAverage;

    /**
     * 역량별 평균 (5점 척도). 예: {@code {"COLLABORATION":4.4,"LEADERSHIP":4.2,...}}.
     * LEADERSHIP 은 PeerEvaluation.initiativeScore(주도성) 에 대응한다 — 저장 컬럼명과 화면 라벨이 다르다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "competency_scores", columnDefinition = "jsonb")
    private Map<CompetencyCategory, BigDecimal> competencyScores;

    /** 평가자들이 고른 키워드(태그 칩). 예: {@code ["리더십","책임감"]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "peer_keywords", columnDefinition = "jsonb")
    private List<String> peerKeywords;

    // ── 5단계(LLM) 산출물. 컬럼 하나가 화면 섹션 하나에 대응한다. ──
    // 중첩 구조인 것들은 jsonb 에 직렬화해 넣는다(ReportActivityLog.metadata 와 같은 방식).
    // 최신 LLM 응답은 근거 부족 시 안내 문구를 채운다. nullable 은 과거 응답 호환을 위해 유지한다.

    /** 개인 리포트 상단의 "AI 한줄 평가". */
    @Column(name = "headline", columnDefinition = "TEXT")
    private String headline;

    /** 팀 리포트 멤버 카드용 활동 요약. 개인 리포트 headline과 목적·문체를 분리한다. */
    @Column(name = "team_member_headline", columnDefinition = "TEXT")
    private String teamMemberHeadline;

    /** 개인 리포트 ② 강점 분석 — MemberReportText.StrengthCard 3개. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strengths", columnDefinition = "jsonb")
    private String strengths;

    /** 개인 리포트 ③ 취약점 진단 — MemberReportText.Weakness. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weakness", columnDefinition = "jsonb")
    private String weakness;

    /** 개인 리포트 ④ 성장 인사이트 — MemberReportText.GrowthInsight. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "growth", columnDefinition = "jsonb")
    private String growth;

    /** 개인 리포트 ⑤ 문장 변환 — MemberReportText.WritingSuggestion. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "writing", columnDefinition = "jsonb")
    private String writing;

    /** LLM 응답 원문. 문장 품질 이슈를 나중에 추적하려면 파싱 전 원본이 남아 있어야 한다. */
    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    /** 실제 응답한 모델명. 모델을 바꾼 뒤 톤이 달라졌을 때 어느 리포트가 어느 모델인지 구분한다. */
    @Column(name = "llm_model", length = 100)
    private String llmModel;

    /** 이 멤버에게만 제공하는 팀+본인 개인 리포트 ZIP. */
    @Column(name = "pdf_object_key", length = 1024)
    private String pdfObjectKey;

    @Column(name = "pdf_file_name")
    private String pdfFileName;

    public static ReportMemberResult create(Report report, ProjectMember projectMember) {
        if (report == null || projectMember == null) {
            throw new IllegalArgumentException("report and projectMember must not be null");
        }
        return ReportMemberResult.builder().report(report).projectMember(projectMember).build();
    }

    public void applyScores(
            BigDecimal internalScore,
            BigDecimal externalScore,
            BigDecimal peerScore,
            BigDecimal selfFeedbackScore,
            BigDecimal finalScore,
            boolean externalToolConnected,
            ReliabilityTier reliabilityTier,
            String cautionText
    ) {
        this.internalScore = internalScore;
        this.externalScore = externalScore;
        this.peerScore = peerScore;
        this.selfFeedbackScore = selfFeedbackScore;
        this.finalScore = finalScore;
        this.externalToolConnected = externalToolConnected;
        this.reliabilityTier = reliabilityTier;
        this.cautionText = cautionText;
    }

    /**
     * 업무 완료/마감 준수 건수 기록. InternalReportData 조립 시점에 함께 저장한다 —
     * 팀 리포트 표의 완료율·마감 준수율(멤버별 컬럼을 합산해서 계산)과
     * 멤버 상세의 "12/13건" 표기에 쓰인다.
     */
    public void applyTaskStatistics(
            int totalTaskCount,
            int completedTaskCount,
            int deadlineMetTaskCount,
            int deadlineTargetTaskCount,
            Double completionRate,
            Double deadlineComplianceRate
    ) {
        this.totalTaskCount = totalTaskCount;
        this.completedTaskCount = completedTaskCount;
        this.deadlineMetTaskCount = deadlineMetTaskCount;
        this.deadlineTargetTaskCount = deadlineTargetTaskCount;
        this.completionRate = toPercent(completionRate);
        this.deadlineComplianceRate = toPercent(deadlineComplianceRate);
    }

    public void applyTaskStatistics(
            int totalTaskCount,
            int completedTaskCount,
            int deadlineMetTaskCount,
            Double completionRate,
            Double deadlineComplianceRate
    ) {
        applyTaskStatistics(totalTaskCount, completedTaskCount, deadlineMetTaskCount,
                totalTaskCount, completionRate, deadlineComplianceRate);
    }

    /** 기존 호출부 호환용. 새 생성 경로는 nullable 비율까지 함께 저장한다. */
    public void applyTaskCounts(int totalTaskCount, int completedTaskCount, int deadlineMetTaskCount) {
        applyTaskStatistics(totalTaskCount, completedTaskCount, deadlineMetTaskCount, totalTaskCount,
                totalTaskCount == 0 ? null : completedTaskCount / (double) totalTaskCount,
                totalTaskCount == 0 ? null : deadlineMetTaskCount / (double) totalTaskCount);
    }

    public void applyTeamAnalysis(
            BigDecimal contributionRate,
            BigDecimal peerZScore,
            BigDecimal peerPercentile,
            BigDecimal collaborationStability,
            BigDecimal vulnerability,
            CompetencyCategory vulnerableCompetency
    ) {
        this.contributionRate = contributionRate;
        this.peerZScore = peerZScore;
        this.peerPercentile = peerPercentile;
        this.collaborationStability = collaborationStability;
        this.vulnerability = vulnerability;
        this.vulnerableCompetency = vulnerableCompetency;
    }

    private BigDecimal toPercent(Double ratio) {
        return ratio == null ? null : BigDecimal.valueOf(ratio)
                .multiply(new BigDecimal("100"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 팀 리포트 표시용 Peer 집계(5점 척도) 기록. 점수 확정과 같은 시점에 함께 저장한다 —
     * 화면의 역량 점수/태그 칩이 이 값으로 그려진다.
     * <p>
     * 받은 평가가 없는 멤버(none())도 리포트에는 나와야 하므로 예외를 던지지 않는다.
     * null/빈 값을 그대로 받아 저장하고(평균은 null, 나머지는 빈 컬렉션), 화면이 빈 섹션을 숨긴다.
     */
    public void applyPeerBreakdown(
            BigDecimal peerAverage,
            Map<CompetencyCategory, BigDecimal> competencyScores,
            List<String> peerKeywords
    ) {
        this.peerAverage = peerAverage;
        // EnumMap 의 copy 생성자는 인자가 비어 있고 EnumMap 이 아니면 예외를 던진다(none() 은 빈 Map).
        // putAll 은 빈 Map 도 안전하다.
        Map<CompetencyCategory, BigDecimal> copy = new EnumMap<>(CompetencyCategory.class);
        if (competencyScores != null) {
            copy.putAll(competencyScores);
        }
        this.competencyScores = copy;
        this.peerKeywords = peerKeywords == null ? List.of() : List.copyOf(peerKeywords);
    }

    /**
     * 5단계 LLM 텍스트 기록. 재실행 시 덮어쓴다 — 같은 멤버의 결과는 항상 한 행이고
     * (uk_report_member), 새로 생성했다는 건 이전 텍스트를 대체하겠다는 뜻이다.
     */
    public void applyLlmText(LlmTextPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        this.headline = payload.headline();
        this.teamMemberHeadline = payload.teamMemberHeadline();
        this.strengths = payload.strengths();
        this.weakness = payload.weakness();
        this.growth = payload.growth();
        this.writing = payload.writing();
        this.rawResponse = payload.rawResponse();
        this.llmModel = payload.llmModel();
    }

    /** 텍스트 생성이 끝났는지. 발행 전 "전원 완료" 판정의 기준이다. */
    public boolean hasLlmText() {
        return headline != null && !headline.isBlank()
                && teamMemberHeadline != null && !teamMemberHeadline.isBlank();
    }

    public void attachPdfArchive(String objectKey, String fileName) {
        if (objectKey == null || objectKey.isBlank() || fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("PDF archive metadata must not be blank");
        }
        this.pdfObjectKey = objectKey;
        this.pdfFileName = fileName;
    }

    /**
     * 직렬화까지 끝난 LLM 텍스트 묶음. 엔티티가 ObjectMapper 를 알 필요가 없도록
     * 서비스에서 직렬화해 넘긴다.
     */
    public record LlmTextPayload(
            String headline,
            String teamMemberHeadline,
            String strengths,
            String weakness,
            String growth,
            String writing,
            String rawResponse,
            String llmModel
    ) {
        public LlmTextPayload(
                String headline, String strengths, String weakness, String growth, String writing,
                String rawResponse, String llmModel
        ) {
            this(headline, headline, strengths, weakness, growth, writing, rawResponse, llmModel);
        }
    }
}
