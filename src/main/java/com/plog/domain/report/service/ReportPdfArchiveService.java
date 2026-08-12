package com.plog.domain.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.llm.MemberReportText;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.global.api.error.ReportErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportPdfArchiveService {

    private final ReportRepository reportRepository;
    private final ReportMemberResultRepository resultRepository;
    private final ReportPdfRenderer renderer;
    private final FileStorageService fileStorageService;
    private final ReportTextWriter textWriter;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<MemberReportText.StrengthCard>> STRENGTH_LIST =
            new TypeReference<>() {
            };
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String[] SERIES_COLORS = {"#1687EE", "#58C3A3", "#FFB45C", "#9B8AFB", "#F27F8C"};

    public void generateAndAttach(Long reportId) {
        Report report = reportRepository.findWithProjectById(reportId)
                .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
        List<ReportMemberResult> members = resultRepository.findAllByReportIdOrderByProjectMemberIdAsc(reportId);
        String code = report.getTeamReportCode();
        String fileName = code + "-reports.zip";
        String objectKey = "reports/" + reportId + "/" + fileName;
        byte[] archive;
        try {
            archive = zip(report, members);
            log.info("리포트 PDF ZIP 렌더링 완료: reportId={}, memberCount={}, archiveBytes={}",
                    reportId, members.size(), archive.length);
        } catch (RuntimeException exception) {
            log.error("리포트 PDF ZIP 렌더링 실패: reportId={}, memberCount={}",
                    reportId, members.size(), exception);
            throw exception;
        }
        try {
            fileStorageService.putGeneratedObject(objectKey, "application/zip", archive);
            log.info("리포트 PDF ZIP S3 업로드 완료: reportId={}, objectKey={}", reportId, objectKey);
        } catch (RuntimeException exception) {
            log.error("리포트 PDF ZIP S3 업로드 실패: reportId={}, objectKey={}",
                    reportId, objectKey, exception);
            throw exception;
        }
        try {
            textWriter.attachPdfArchive(reportId, objectKey, fileName);
            log.info("리포트 PDF ZIP 메타데이터 저장 완료: reportId={}, objectKey={}, fileName={}",
                    reportId, objectKey, fileName);
        } catch (RuntimeException exception) {
            log.error("리포트 PDF ZIP 메타데이터 저장 실패: reportId={}, objectKey={}, fileName={}",
                    reportId, objectKey, fileName, exception);
            throw exception;
        }
    }

    private byte[] zip(Report report, List<ReportMemberResult> members) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            add(zip, "team-report.pdf", renderer.render(teamHtml(report, members)));
            for (ReportMemberResult member : members) {
                add(zip, "member-" + member.getProjectMember().getId() + "-report.pdf",
                        renderer.render(memberHtml(report, member)));
            }
            zip.finish();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("리포트 ZIP 생성에 실패했습니다.", exception);
        }
    }

    private void add(ZipOutputStream zip, String name, byte[] bytes) throws java.io.IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String teamHtml(Report report, List<ReportMemberResult> members) {
        StringBuilder taskRows = new StringBuilder();
        StringBuilder contributionRows = new StringBuilder();
        StringBuilder memberCards = new StringBuilder();
        int index = 0;
        for (ReportMemberResult member : members) {
            String name = member.getProjectMember().getUser().getName();
            taskRows.append("<tr><td class='name'>").append(esc(name)).append("</td><td>")
                    .append(member.getTotalTaskCount()).append("개</td><td>")
                    .append(member.getCompletedTaskCount()).append("개</td><td class='")
                    .append(rateClass(member.getCompletionRate())).append("'>")
                    .append(displayPercent(member.getCompletionRate())).append("</td><td class='")
                    .append(rateClass(member.getDeadlineComplianceRate())).append("'>")
                    .append(displayPercent(member.getDeadlineComplianceRate())).append("</td></tr>");

            String color = SERIES_COLORS[index++ % SERIES_COLORS.length];
            contributionRows.append(scoreRow(name, member.getContributionRate(), color, "%"));
            memberCards.append(teamMemberCard(member));
        }
        String body = hero(report, report.getTeamReportCode(), "팀원 " + members.size() + "명")
                + statCards("전체 업무 완료율", report.getTeamCompletionRate(), "%",
                "전체 업무 " + totalTasks(members) + "건 기준 · " + completedTasks(members) + "건 완료",
                "마감 준수율", report.getTeamDeadlineComplianceRate(), "%",
                "기한 내 완료 " + deadlineMetTasks(members) + "/" + deadlineTargetTasks(members) + "건")
                + aiNote("AI 인사이트", joinedInsight(report))
                + section(1, "팀 업무 완수 현황", "전체적으로 목표한 업무를 달성 중이에요",
                "<table class='data-table'><thead><tr><th>팀원</th><th>전체</th><th>완료</th>"
                        + "<th>완료율</th><th>마감 준수</th></tr></thead><tbody>" + taskRows + "</tbody></table>")
                + section(2, "팀 기여도 분포", "업무 완료, 활동 지수, Peer 평가를 종합한 결과예요",
                "<div class='card score-list'>" + contributionRows + "</div>")
                + section(4, "팀원별 활동 요약", "완료율은 전체 부여 업무 기준으로 계산했어요",
                memberCards.toString());
        return page(report.getProject().getProjectName() + " 팀 리포트", body);
    }

    private String memberHtml(Report report, ReportMemberResult member) {
        String name = member.getProjectMember().getUser().getName();
        String body = hero(report, report.getPersonalReportCode(), name)
                + statCards("종합 기여도 점수", member.getFinalScore(), "/100", "",
                "협업 안정도", member.getCollaborationStability(), "%", "")
                + aiNote("AI 한줄 평가", fallback(member.getHeadline(),
                "분석할 활동이 충분하지 않아 한줄 평가를 생성하지 못했어요"))
                + optionalCaution(member.getCautionText())
                + section(1, "기여도 상세 분석", "업무 영역별 기여도 점수를 확인해 보세요",
                "<div class='card score-list'>" + competencyRows(member.getCompetencyScores(), true) + "</div>")
                + strengthSection(name, member)
                + weaknessSection(name, member)
                + growthSection(member)
                + writingSection(member);
        return page(report.getProject().getProjectName() + " - " + name + " 개인 리포트", body);
    }

    private String page(String title, String body) {
        return "<html><head><meta charset='UTF-8'/><title>" + esc(title) + "</title><style>"
                + "@page{size:A4;margin:14mm 0}@page:first{margin-top:0}*{box-sizing:border-box}body{margin:0;font-family:PlogFont,sans-serif;"
                + "color:#202632;background:#fff;font-size:11px;line-height:1.55}"
                + ".hero{background:#132B4F;color:#fff;padding:16mm 18mm 22mm}.brand{font-size:11px;color:#8EC8FF;"
                + "letter-spacing:.3px}.hero h1{margin:4px 0 10px;font-size:25px;line-height:1.25}.badges span{display:inline-block;"
                + "margin-right:6px;padding:4px 9px;border-radius:12px;background:#314767;font-size:9px}"
                + ".content{padding:0 18mm}.stats{width:100%;border-spacing:7px;margin:-13mm -7px 8mm;table-layout:fixed}"
                + ".stat{background:#fff;border:1px solid #E8EDF4;border-radius:12px;padding:13px;box-shadow:0 3px 10px #DDE4EE}"
                + ".stat-label{color:#788495;font-size:10px}.stat-value{font-size:25px;font-weight:700;color:#142B4F;margin:3px 0}"
                + ".stat-unit{font-size:11px;color:#7C8797}.caption{font-size:9px;color:#8D98A7}"
                + ".ai-note{background:#EEF7FF;border:1px solid #CFE8FF;border-radius:10px;padding:11px 13px;margin:0 0 8mm;"
                + "page-break-inside:avoid}.ai-label{font-size:10px;font-weight:700;color:#1687EE;margin-bottom:3px}"
                + ".section{margin:0 0 9mm}.section-title{page-break-after:avoid;margin-bottom:9px}.step{display:inline-block;"
                + "width:19px;height:19px;line-height:19px;text-align:center;border-radius:50%;background:#1687EE;color:#fff;"
                + "font-weight:700;margin-right:7px}.section-title h2{display:inline;font-size:17px;color:#202632}.section-desc{margin:2px 0 0 28px;color:#8A95A4;font-size:10px}"
                + ".card,.member-card{border:1px solid #E3E9F0;border-radius:10px;background:#fff;page-break-inside:avoid}"
                + ".data-table{width:100%;border-collapse:separate;border-spacing:0;border:1px solid #E3E9F0;border-radius:8px;"
                + "overflow:hidden}.data-table th{background:#F1F4F8;color:#788495;font-size:9px;padding:7px;text-align:center}"
                + ".data-table th:first-child,.data-table td:first-child{text-align:left}.data-table td{padding:8px 7px;"
                + "text-align:center;border-top:1px solid #EDF1F5}.name{font-weight:600}.success{color:#28A67A}.danger{color:#ED6A69}"
                + ".score-list{padding:10px 13px}.score-row{width:100%;margin:5px 0;border-spacing:0}.score-label{width:26%;font-weight:600}"
                + ".bar-cell{width:58%;padding:0 9px}.bar{height:7px;background:#EDF1F6;border-radius:5px;overflow:hidden}.bar-fill{height:7px}"
                + ".score-value{text-align:right;font-weight:700}.member-card{padding:12px 13px;margin-bottom:8px}.member-head{width:100%;"
                + "border-spacing:0}.member-name{font-size:14px;font-weight:700}.peer{text-align:right;color:#142B4F;font-size:14px;font-weight:700}"
                + ".chip{display:inline-block;background:#EAF5FF;color:#1687EE;border-radius:9px;padding:2px 6px;font-size:8px;margin-left:4px}"
                + ".activity{font-size:9px;color:#8994A3}.peer-total{background:#18365D;color:#fff;border-radius:9px;padding:9px 12px;"
                + "margin-top:8px;font-weight:700}.peer-total span{float:right}.warning{background:#FEF6E7;color:#B77A18;border-radius:9px;"
                + "padding:9px 12px;margin-bottom:7mm;page-break-inside:avoid}.strength-grid{width:100%;border-spacing:7px;margin:-7px}"
                + ".strength-card{border:1px solid #E3E9F0;border-radius:10px;text-align:center;padding:12px 8px;vertical-align:top;"
                + "page-break-inside:avoid}.icon{width:28px;height:28px;line-height:28px;margin:0 auto 6px;border-radius:50%;"
                + "background:#EAF5FF;color:#1687EE;font-weight:700}.strength-title{font-size:12px;font-weight:700;margin-bottom:4px}"
                + ".muted{color:#7F8A99;font-size:9px}.weakness{width:100%;border-spacing:0;padding:13px}.gauge{width:27%;"
                + "text-align:center;vertical-align:middle;border-right:1px solid #E8EDF3}.gauge-value{font-size:23px;font-weight:700;color:#1687EE}"
                + ".weakness-copy{padding-left:14px}.weakness-copy h3{margin:2px 0 5px;font-size:13px}.insight-row{width:100%;"
                + "border-spacing:0;padding:10px 13px;border-bottom:1px solid #E8EDF3}.insight-row:last-child{border-bottom:0}"
                + ".insight-label{width:25%;font-weight:700}.footer{text-align:center;color:#A1AAB6;font-size:8px;margin-top:8mm}"
                + "</style></head><body>" + body + "</div><div class='footer'>Plog contribution report</div></body></html>";
    }

    private String hero(Report report, String code, String context) {
        return "<div class='hero'><div class='brand'>" + esc(code) + "</div><h1>"
                + esc(report.getProject().getProjectName()) + "</h1><div class='badges'><span>발행 "
                + date(report.getCompletedAt()) + "</span><span>" + esc(context) + "</span><span>"
                + date(report.getProject().getStartDay()) + " - " + date(report.getProject().getEndDay())
                + "</span></div></div><div class='content'>";
    }

    private String statCards(String leftLabel, Object leftValue, String leftUnit, String leftCaption,
                             String rightLabel, Object rightValue, String rightUnit, String rightCaption) {
        return "<table class='stats'><tr>" + stat(leftLabel, leftValue, leftUnit, leftCaption)
                + stat(rightLabel, rightValue, rightUnit, rightCaption) + "</tr></table>";
    }

    private String stat(String label, Object value, String unit, String caption) {
        return "<td><div class='stat'><div class='stat-label'>" + esc(label) + "</div><div class='stat-value'>"
                + displayNumber(value) + metricUnit(value, unit) + "</div><div class='caption'>"
                + esc(caption) + "</div></div></td>";
    }

    private String section(int step, String title, String description, String content) {
        return "<div class='section'><div class='section-title'><span class='step'>" + step + "</span><h2>"
                + esc(title) + "</h2><p class='section-desc'>" + esc(description) + "</p></div>" + content + "</div>";
    }

    private String aiNote(String label, String content) {
        return "<div class='ai-note'><div class='ai-label'>✦ " + esc(label) + "</div><div>"
                + esc(content) + "</div></div>";
    }

    private String teamMemberCard(ReportMemberResult member) {
        StringBuilder chips = new StringBuilder();
        List<String> keywords = member.getPeerKeywords() == null ? List.of() : member.getPeerKeywords();
        keywords.stream().limit(2).forEach(keyword -> chips.append("<span class='chip'>").append(esc(keyword)).append("</span>"));
        return "<div class='member-card'><table class='member-head'><tr><td><div class='member-name'>"
                + esc(member.getProjectMember().getUser().getName()) + chips + "</div><div class='activity'>전체 "
                + member.getTotalTaskCount() + "개 · 완료 " + member.getCompletedTaskCount() + "개 · 완료율 "
                + displayPercent(member.getCompletionRate()) + "</div></td><td class='peer'>"
                + metric(member.getPeerAverage(), " / 5.0") + "</td></tr></table>"
                + aiNote("AI 한줄 평가", fallback(member.getTeamMemberHeadline(),
                "평가 근거가 부족해 한줄 평가를 생성하지 못했어요"))
                + "<div class='score-list'>" + competencyRows(member.getCompetencyScores(), false) + "</div>"
                + "<div class='peer-total'>종합 Peer 평균 <span>★ " + metric(member.getPeerAverage(), " / 5.0")
                + "</span></div></div>";
    }

    private String competencyRows(Map<CompetencyCategory, BigDecimal> scores, boolean scaleToHundred) {
        Map<CompetencyCategory, BigDecimal> safe = scores == null ? Map.of() : scores;
        StringBuilder rows = new StringBuilder();
        List<CompetencyCategory> categories = Arrays.asList(CompetencyCategory.COLLABORATION,
                CompetencyCategory.LEADERSHIP, CompetencyCategory.COMMUNICATION, CompetencyCategory.OUTPUT);
        for (int index = 0; index < categories.size(); index++) {
            CompetencyCategory category = categories.get(index);
            BigDecimal raw = safe.get(category);
            BigDecimal shown = raw == null ? null
                    : scaleToHundred ? raw.multiply(BigDecimal.valueOf(20)) : raw;
            BigDecimal bar = raw == null ? null
                    : scaleToHundred ? shown : raw.multiply(BigDecimal.valueOf(20));
            rows.append(scoreRow(competencyLabel(category), shown, SERIES_COLORS[index], scaleToHundred ? "" : " / 5.0", bar));
        }
        return rows.toString();
    }

    private String scoreRow(String label, BigDecimal value, String color, String unit) {
        return scoreRow(label, value, color, unit, value);
    }

    private String scoreRow(String label, BigDecimal value, String color, String unit, BigDecimal barValue) {
        String bar = barValue == null
                ? "<div class='bar'></div>"
                : "<div class='bar'><div class='bar-fill' style='width:" + clampPercent(barValue)
                        + "%;background:" + color + "'></div></div>";
        return "<table class='score-row'><tr><td class='score-label'>" + esc(label) + "</td><td class='bar-cell'>"
                + bar + "</td><td class='score-value'>" + metric(value, unit) + "</td></tr></table>";
    }

    private String strengthSection(String name, ReportMemberResult member) {
        List<MemberReportText.StrengthCard> strengths = readJson(member.getStrengths(), STRENGTH_LIST, List.of())
                .stream().filter(Objects::nonNull).toList();
        if (strengths.isEmpty()) {
            return "";
        }
        StringBuilder cards = new StringBuilder("<table class='strength-grid'><tr>");
        for (int index = 0; index < strengths.size(); index++) {
            MemberReportText.StrengthCard strength = strengths.get(index);
            cards.append("<td class='strength-card'><div class='icon'>").append(index + 1)
                    .append("</div><div class='strength-title'>").append(esc(strength.title()))
                    .append("</div><div class='muted'>").append(esc(strength.description())).append("</div></td>");
        }
        cards.append("</tr></table>");
        return section(2, "강점 분석", name + "님의 강점을 AI가 분석했어요", cards.toString());
    }

    private String weaknessSection(String name, ReportMemberResult member) {
        MemberReportText.Weakness weakness = readJson(member.getWeakness(), MemberReportText.Weakness.class, null);
        String title = weakness == null || weakness.title() == null || weakness.title().isBlank()
                ? "아직 뚜렷한 취약점이 발견되지 않았어요" : weakness.title();
        StringBuilder tips = new StringBuilder("<ul>");
        if (weakness != null) {
            List<String> suggestions = weakness.suggestions() == null ? List.of() : weakness.suggestions();
            suggestions.stream().filter(Objects::nonNull)
                    .forEach(tip -> tips.append("<li>").append(esc(tip)).append("</li>"));
        }
        tips.append("</ul>");
        String content = "<table class='card weakness'><tr><td class='gauge'><div class='muted'>취약도</div>"
                + "<div class='gauge-value'>" + displayPercent(member.getVulnerability()) + "</div></td>"
                + "<td class='weakness-copy'><div class='muted'>주요 취약점</div><h3>" + esc(title) + "</h3>"
                + tips + "</td></tr></table>";
        return section(3, "취약점 진단", name + "님의 약점을 AI가 분석했어요", content);
    }

    private String growthSection(ReportMemberResult member) {
        MemberReportText.GrowthInsight growth = readJson(member.getGrowth(), MemberReportText.GrowthInsight.class, null);
        if (growth == null) {
            return "";
        }
        String content = "<div class='card'>" + insightRow("성장 포인트", growth.growthPoint())
                + insightRow("유지 강점", growth.keepStrength()) + insightRow("다음 액션", growth.nextAction()) + "</div>";
        return section(4, "AI 개인 성장 인사이트", "지속적인 성장을 위한 맞춤 인사이트를 확인해 보세요", content);
    }

    private String writingSection(ReportMemberResult member) {
        MemberReportText.WritingSuggestion writing = readJson(member.getWriting(), MemberReportText.WritingSuggestion.class, null);
        if (writing == null) {
            return "";
        }
        return section(5, "AI 문장 변환", "업무 경험을 자기소개서/포트폴리오 문장으로 변환했어요",
                aiNote("AI 자기소개서 추천 문장", writing.coverLetter())
                        + aiNote("AI 포트폴리오 추천 문장", writing.portfolio()));
    }

    private String insightRow(String label, String text) {
        return "<table class='insight-row'><tr><td class='insight-label'>" + esc(label)
                + "</td><td class='muted'>" + esc(text) + "</td></tr></table>";
    }

    private String optionalCaution(String caution) {
        return caution == null || caution.isBlank() ? "" : "<div class='warning'>⚠ " + esc(caution) + "</div>";
    }

    private <T> T readJson(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            T parsed = objectMapper.readValue(json, type);
            return parsed == null ? fallback : parsed;
        } catch (Exception exception) {
            log.warn("PDF 리포트 JSON 역직렬화 실패, 해당 섹션을 생략합니다: type={}", type.getSimpleName(), exception);
            return fallback;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            T parsed = objectMapper.readValue(json, type);
            return parsed == null ? fallback : parsed;
        } catch (Exception exception) {
            log.warn("PDF 리포트 JSON 역직렬화 실패, 해당 섹션을 생략합니다", exception);
            return fallback;
        }
    }

    private String joinedInsight(Report report) {
        String joined = String.join(" ", Arrays.asList(report.getTeamStrength(), report.getTeamSuggestion()).stream()
                .filter(value -> value != null && !value.isBlank()).toList());
        return fallback(joined, "분석할 활동이 충분하지 않아 인사이트를 생성하지 못했어요");
    }

    private int totalTasks(List<ReportMemberResult> members) {
        return members.stream().mapToInt(ReportMemberResult::getTotalTaskCount).sum();
    }

    private int completedTasks(List<ReportMemberResult> members) {
        return members.stream().mapToInt(ReportMemberResult::getCompletedTaskCount).sum();
    }

    private int deadlineMetTasks(List<ReportMemberResult> members) {
        return members.stream().mapToInt(ReportMemberResult::getDeadlineMetTaskCount).sum();
    }

    private int deadlineTargetTasks(List<ReportMemberResult> members) {
        return members.stream().mapToInt(ReportMemberResult::getDeadlineTargetTaskCount).sum();
    }

    private String rateClass(BigDecimal rate) {
        if (rate == null) {
            return "";
        }
        return rate.compareTo(BigDecimal.valueOf(80)) >= 0 ? "success" : "danger";
    }

    private String competencyLabel(CompetencyCategory category) {
        return switch (category) {
            case COLLABORATION -> "협업 태도";
            case LEADERSHIP -> "리더십";
            case COMMUNICATION -> "커뮤니케이션";
            case OUTPUT -> "산출물 기여";
        };
    }

    private String date(LocalDate value) {
        return value == null ? "측정 불가" : value.format(DATE);
    }

    private String date(LocalDateTime value) {
        return value == null ? "측정 불가" : value.toLocalDate().format(DATE);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String displayNumber(Object value) {
        if (value == null) {
            return "측정 불가";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return esc(value.toString());
    }

    private int percent(BigDecimal value) {
        return value.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
    }

    private int clampPercent(BigDecimal value) {
        return Math.max(0, Math.min(100, percent(value)));
    }

    private String displayPercent(BigDecimal value) {
        return value == null ? "측정 불가" : percent(value) + "%";
    }

    private String metric(Object value, String unit) {
        return value == null ? "측정 불가" : displayNumber(value) + esc(unit);
    }

    private String metricUnit(Object value, String unit) {
        return value == null ? "" : " <span class='stat-unit'>" + esc(unit) + "</span>";
    }

    private String esc(String value) {
        // OpenHTMLToPDF는 HTML이 아니라 XML 파서로 읽는다. &middot; 같은 HTML 전용 named entity는
        // 선언되어 있지 않아 전체 렌더링이 실패하므로 XML에서도 유효한 숫자 엔티티를 사용한다.
        return HtmlUtils.htmlEscapeDecimal(value == null ? "" : value);
    }

}
