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

    /** 기한 내 완료한 업무 수. 화면 "12/13건" 표기의 앞 숫자(분모는 totalTaskCount). */
    @Column(name = "deadline_met_task_count", nullable = false, columnDefinition = "integer default 0")
    private int deadlineMetTaskCount;

    // ── 5단계(LLM) 산출물. 컬럼 하나가 화면 섹션 하나에 대응한다. ──
    // 중첩 구조인 것들은 jsonb 에 직렬화해 넣는다(ReportActivityLog.metadata 와 같은 방식).
    // 근거가 부족하면 LLM 이 비워 보내므로 전부 nullable 이고, 화면은 빈 섹션을 숨긴다.

    /** 팀 리포트 멤버 카드 + 개인 리포트 상단의 "AI 한줄 평가". 목록 조회에서도 쓰여 별도 컬럼이다. */
    @Column(name = "headline", columnDefinition = "TEXT")
    private String headline;

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
    public void applyTaskCounts(int totalTaskCount, int completedTaskCount, int deadlineMetTaskCount) {
        this.totalTaskCount = totalTaskCount;
        this.completedTaskCount = completedTaskCount;
        this.deadlineMetTaskCount = deadlineMetTaskCount;
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
        this.strengths = payload.strengths();
        this.weakness = payload.weakness();
        this.growth = payload.growth();
        this.writing = payload.writing();
        this.rawResponse = payload.rawResponse();
        this.llmModel = payload.llmModel();
    }

    /** 텍스트 생성이 끝났는지. 발행 전 "전원 완료" 판정의 기준이다. */
    public boolean hasLlmText() {
        return headline != null && !headline.isBlank();
    }

    /**
     * 직렬화까지 끝난 LLM 텍스트 묶음. 엔티티가 ObjectMapper 를 알 필요가 없도록
     * 서비스에서 직렬화해 넘긴다.
     */
    public record LlmTextPayload(
            String headline,
            String strengths,
            String weakness,
            String growth,
            String writing,
            String rawResponse,
            String llmModel
    ) {
    }
}
