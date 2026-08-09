package com.plog.domain.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plog.domain.integration.entity.LinkType;
import com.plog.domain.integration.entity.ProjectIntegration;
import com.plog.domain.integration.entity.ProjectMemberIntegrationIdentity;
import com.plog.domain.integration.repository.ProjectIntegrationRepository;
import com.plog.domain.integration.repository.ProjectMemberIntegrationIdentityRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.report.entity.CompetencyCategory;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReliabilityTier;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.port.ExternalReportData;
import com.plog.domain.report.port.ExternalReportDataProvider;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매핑 완료된 외부 활동 로그를 프로젝트 단위로 한 번에 읽어 멤버별 외부 점수와 LLM 근거를 만든다.
 * 점수는 provider별 활동 가중치 합계를 팀 내 최대 원점수 기준으로 0~100 정규화한다.
 */
@Service
@RequiredArgsConstructor
public class ExternalReportDataProviderImpl implements ExternalReportDataProvider {

    private static final List<SourceDomain> EXTERNAL_DOMAINS = List.of(
            SourceDomain.GITHUB, SourceDomain.FIGMA, SourceDomain.GOOGLE, SourceDomain.NOTION);
    private static final Set<LinkType> P2_LINK_TYPES = EnumSet.of(
            LinkType.GITHUB, LinkType.FIGMA, LinkType.GOOGLE_DOCS, LinkType.GOOGLE_SLIDES);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern LOGIN = Pattern.compile("(?<![\\w.])@[A-Za-z0-9_.-]+");

    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectMemberIntegrationIdentityRepository identityRepository;
    private final ReportActivityLogRepository reportActivityLogRepository;
    private final ExternalActivityCompetencyMapper competencyMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ExternalReportData> provide(Long projectId, Collection<Long> projectMemberIds) {
        List<Long> requestedMemberIds = projectMemberIds == null ? List.of() : projectMemberIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (requestedMemberIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ExternalReportData> results = requestedMemberIds.stream()
                .collect(Collectors.toMap(id -> id, id -> ExternalReportData.notConnected(),
                        (left, right) -> left, LinkedHashMap::new));

        List<ProjectIntegration> activeIntegrations = projectIntegrationRepository
                .findAllByProjectIdOrderByLinkTypeAsc(projectId)
                .stream()
                .filter(ProjectIntegration::isConnected)
                .toList();
        if (activeIntegrations.isEmpty()) {
            return results;
        }

        Map<LinkType, ProjectIntegration> integrationsByType = activeIntegrations.stream()
                .collect(Collectors.toMap(ProjectIntegration::getLinkType, integration -> integration));
        List<Long> integrationIds = activeIntegrations.stream().map(ProjectIntegration::getId).toList();
        List<ProjectMemberIntegrationIdentity> identities = identityRepository.findActiveMappedIdentities(
                integrationIds, requestedMemberIds, MemberStatus.ACTIVE);

        Map<Long, Set<LinkType>> mappedTypesByMember = new HashMap<>();
        for (ProjectMemberIntegrationIdentity identity : identities) {
            mappedTypesByMember
                    .computeIfAbsent(identity.getProjectMember().getId(), ignored -> EnumSet.noneOf(LinkType.class))
                    .add(identity.getProjectIntegration().getLinkType());
        }
        for (Long memberId : requestedMemberIds) {
            if (!mappedTypesByMember.containsKey(memberId)) {
                results.put(memberId, ExternalReportData.notMapped());
            }
        }

        if (mappedTypesByMember.isEmpty()) {
            return results;
        }

        List<ReportActivityLog> logs = reportActivityLogRepository.findExternalLogsForActiveProjectMembers(
                new ArrayList<>(mappedTypesByMember.keySet()), EXTERNAL_DOMAINS);

        Map<Long, MemberAccumulator> accumulators = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<LinkType>> entry : mappedTypesByMember.entrySet()) {
            accumulators.put(entry.getKey(), new MemberAccumulator(entry.getValue()));
        }
        preScanMergeCommitShas(projectId, integrationsByType.keySet(), accumulators, logs);
        for (ReportActivityLog log : logs) {
            if (log.getProjectMember() == null || log.getProjectMember().getId() == null) {
                continue;
            }
            MemberAccumulator accumulator = accumulators.get(log.getProjectMember().getId());
            if (accumulator == null) {
                continue;
            }
            LinkType linkType = linkTypeFromSourceRef(projectId, log.getSourceRefId(), integrationsByType.keySet());
            if (linkType == null || !accumulator.mappedLinkTypes.contains(linkType)) {
                continue;
            }
            accumulator.add(log, linkType);
        }

        BigDecimal teamMaxRawScore = accumulators.values().stream()
                .map(MemberAccumulator::scoreableRawScore)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        for (Map.Entry<Long, MemberAccumulator> entry : accumulators.entrySet()) {
            results.put(entry.getKey(), entry.getValue().toExternalReportData(teamMaxRawScore));
        }
        return results;
    }

    private LinkType linkTypeFromSourceRef(Long projectId, String sourceRefId, Set<LinkType> activeTypes) {
        if (sourceRefId == null) {
            return null;
        }
        for (LinkType linkType : activeTypes) {
            String prefix = sourceRefPrefix(projectId, linkType);
            if (sourceRefId.startsWith(prefix)) {
                return linkType;
            }
        }
        return null;
    }

    private String sourceRefPrefix(Long projectId, LinkType linkType) {
        return "integration:%s:%s:".formatted(projectId, linkType.name());
    }

    private void preScanMergeCommitShas(
            Long projectId,
            Set<LinkType> activeTypes,
            Map<Long, MemberAccumulator> accumulators,
            List<ReportActivityLog> logs
    ) {
        for (ReportActivityLog log : logs) {
            if (log.getProjectMember() == null || log.getRawActivityType() != RawActivityType.GITHUB_PULL_REQUEST) {
                continue;
            }
            MemberAccumulator accumulator = accumulators.get(log.getProjectMember().getId());
            LinkType linkType = linkTypeFromSourceRef(projectId, log.getSourceRefId(), activeTypes);
            if (accumulator == null || linkType == null || !accumulator.mappedLinkTypes.contains(linkType)) {
                continue;
            }
            String mergeCommitSha = textAt(metadata(log.getMetadata()), "merge_commit_sha", "mergeCommitSha");
            if (!mergeCommitSha.isBlank()) {
                accumulator.mergeCommitShas.add(mergeCommitSha);
            }
        }
    }

    private final class MemberAccumulator {
        private final Set<LinkType> mappedLinkTypes;
        private final Map<SourceDomain, Long> countByDomain = new EnumMap<>(SourceDomain.class);
        private final Map<CompetencyCategory, Long> competencyCounts = new EnumMap<>(CompetencyCategory.class);
        private final Map<CompetencyCategory, List<EvidenceCandidate>> evidenceByCategory =
                new EnumMap<>(CompetencyCategory.class);
        private final Set<String> mergeCommitShas = new HashSet<>();
        private final Set<String> scoredFigmaReactionDays = new HashSet<>();
        private BigDecimal rawScore = BigDecimal.ZERO;

        private MemberAccumulator(Set<LinkType> mappedLinkTypes) {
            this.mappedLinkTypes = mappedLinkTypes;
        }

        private void add(ReportActivityLog log, LinkType linkType) {
            RawActivityType type = log.getRawActivityType();
            BigDecimal weight = weightOf(log);
            if (type == RawActivityType.GITHUB_COMMIT && mergeCommitShas.contains(commitSha(log))) {
                weight = BigDecimal.ZERO;
            }
            if (type == RawActivityType.FIGMA_COMMENT_REACTION) {
                String reactionDayKey = linkType + ":" + log.getOccurredAt().toLocalDate();
                if (!scoredFigmaReactionDays.add(reactionDayKey)) {
                    weight = BigDecimal.ZERO;
                }
            }

            countByDomain.merge(log.getSourceDomain(), 1L, Long::sum);
            rawScore = rawScore.add(weight);

            Set<CompetencyCategory> categories = competencyMapper.map(type, log.getMetadata());
            String evidenceText = type == RawActivityType.FIGMA_COMMENT_REACTION || categories.isEmpty()
                    ? null
                    : evidenceText(log, linkType);
            for (CompetencyCategory category : categories) {
                competencyCounts.merge(category, 1L, Long::sum);
                if (type != RawActivityType.FIGMA_COMMENT_REACTION) {
                    evidenceByCategory
                            .computeIfAbsent(category, ignored -> new ArrayList<>())
                            .add(new EvidenceCandidate(
                                    log.getSourceDomain(), type, linkType,
                                    log.getOccurredAt().toLocalDate(), weight, evidenceText));
                }
            }
        }

        private BigDecimal scoreableRawScore() {
            return rawScore.max(BigDecimal.ZERO);
        }

        private ExternalReportData toExternalReportData(BigDecimal teamMaxRawScore) {
            ReliabilityTier reliabilityTier = reliabilityTier();
            Map<CompetencyCategory, List<String>> evidence = evidence();
            if (rawScore.compareTo(BigDecimal.ZERO) <= 0 || teamMaxRawScore.compareTo(BigDecimal.ZERO) <= 0) {
                return ExternalReportData.connectedWithoutScore(
                        countByDomain, competencyCounts, evidence, reliabilityTier, cautionText(false));
            }
            BigDecimal normalized = rawScore
                    .multiply(new BigDecimal("100"))
                    .divide(teamMaxRawScore, 2, RoundingMode.HALF_UP);
            return new ExternalReportData(
                    true,
                    true,
                    countByDomain,
                    competencyCounts,
                    evidence,
                    normalized,
                    reliabilityTier,
                    cautionText(true)
            );
        }

        private ReliabilityTier reliabilityTier() {
            boolean hasP2Mapping = mappedLinkTypes.stream().anyMatch(P2_LINK_TYPES::contains);
            return hasP2Mapping ? ReliabilityTier.P2 : ReliabilityTier.P3;
        }

        private String cautionText(boolean hasScore) {
            if (reliabilityTier() == ReliabilityTier.P3) {
                return "Notion 활동은 정성 근거로만 반영되며 외부 점수 산정에서는 제외했습니다.";
            }
            if (!hasScore) {
                return "외부 계정은 매핑됐지만 점수화 가능한 외부 활동이 부족해 Plog 내부 활동 중심으로 분석했습니다.";
            }
            return null;
        }

        private Map<CompetencyCategory, List<String>> evidence() {
            Map<CompetencyCategory, List<String>> result = new EnumMap<>(CompetencyCategory.class);
            for (Map.Entry<CompetencyCategory, List<EvidenceCandidate>> entry : evidenceByCategory.entrySet()) {
                result.put(entry.getKey(), selectEvidence(entry.getValue()));
            }
            return result;
        }
    }

    private BigDecimal weightOf(ReportActivityLog log) {
        return switch (log.getRawActivityType()) {
            case GITHUB_COMMIT, GITHUB_PULL_REQUEST, FIGMA_FILE_VERSION, GOOGLE_DRIVE_REVISION ->
                    new BigDecimal("3");
            case GITHUB_PULL_REQUEST_REVIEW, GITHUB_ISSUE -> new BigDecimal("2");
            case GITHUB_ISSUE_COMMENT, FIGMA_COMMENT, GOOGLE_DRIVE_COMMENT -> BigDecimal.ONE;
            case FIGMA_COMMENT_REACTION -> new BigDecimal("0.5");
            case GOOGLE_DRIVE_ACTIVITY -> googleDriveActivityWeight(log.getMetadata());
            case NOTION_COMMENT, NOTION_BLOCK_SNAPSHOT, NOTION_PAGE_SNAPSHOT, NOTION_DATA_SOURCE_SNAPSHOT ->
                    BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal googleDriveActivityWeight(String metadata) {
        String action = googleDriveAction(metadata);
        return ("create".equals(action) || "edit".equals(action)) ? new BigDecimal("3") : BigDecimal.ZERO;
    }

    private String googleDriveAction(String metadata) {
        JsonNode root = metadata(metadata);
        JsonNode primaryActionDetail = root.path("primaryActionDetail");
        if (primaryActionDetail.isObject()) {
            var names = primaryActionDetail.fieldNames();
            while (names.hasNext()) {
                String action = names.next();
                if ("create".equals(action) || "edit".equals(action)) {
                    return action;
                }
            }
        }
        return root.path("action").asText("");
    }

    private List<String> selectEvidence(List<EvidenceCandidate> candidates) {
        List<EvidenceCandidate> sorted = candidates.stream()
                .sorted(evidencePriority())
                .toList();
        List<String> selected = new ArrayList<>();
        Set<LinkType> usedLinks = new LinkedHashSet<>();
        for (EvidenceCandidate candidate : sorted) {
            if (selected.size() >= 5) {
                break;
            }
            if (usedLinks.add(candidate.linkType())) {
                selected.add(candidate.text());
            }
        }
        Set<RawActivityType> usedTypes = new LinkedHashSet<>();
        for (EvidenceCandidate candidate : sorted) {
            if (selected.size() >= 5) {
                break;
            }
            if (usedTypes.add(candidate.type()) && !selected.contains(candidate.text())) {
                selected.add(candidate.text());
            }
        }
        for (EvidenceCandidate candidate : sorted) {
            if (selected.size() >= 5) {
                break;
            }
            if (!selected.contains(candidate.text())) {
                selected.add(candidate.text());
            }
        }
        return List.copyOf(selected);
    }

    private Comparator<EvidenceCandidate> evidencePriority() {
        return Comparator
                .comparing(EvidenceCandidate::weight).reversed()
                .thenComparing(EvidenceCandidate::domain)
                .thenComparing(EvidenceCandidate::type)
                .thenComparing(EvidenceCandidate::occurredDate, Comparator.reverseOrder())
                .thenComparing(EvidenceCandidate::text);
    }

    private String evidenceText(ReportActivityLog log, LinkType linkType) {
        String label = firstNonBlank(
                typeSpecificSummary(log),
                log.getRawActivityType().name());
        return sanitize("%s: %s %s".formatted(linkType.name(), koreanType(log.getRawActivityType()), label)
                .replaceAll("\\s+", " ")
                .trim());
    }

    private String typeSpecificSummary(ReportActivityLog log) {
        JsonNode root = metadata(log.getMetadata());
        return switch (log.getRawActivityType()) {
            case GITHUB_COMMIT -> textAt(root, "message", "commit.message", "title");
            case GITHUB_PULL_REQUEST, GITHUB_ISSUE -> textAt(root, "title", "name");
            case GITHUB_PULL_REQUEST_REVIEW, GITHUB_ISSUE_COMMENT -> textAt(root, "body", "comment.body", "message");
            case FIGMA_COMMENT -> textAt(root, "message", "comment.message", "text");
            case FIGMA_FILE_VERSION -> textAt(root, "label", "description", "name");
            case GOOGLE_DRIVE_ACTIVITY -> textAt(root, "target.name", "targets.0.name", "title", "name");
            case GOOGLE_DRIVE_REVISION -> textAt(root, "originalFilename", "fileName", "target.name", "title");
            case GOOGLE_DRIVE_COMMENT -> textAt(root, "content", "quotedFileContent.value", "comment.content", "text");
            case NOTION_COMMENT -> textAt(root, "rich_text.0.plain_text", "text.content", "plain_text", "content");
            case NOTION_PAGE_SNAPSHOT, NOTION_BLOCK_SNAPSHOT, NOTION_DATA_SOURCE_SNAPSHOT ->
                    textAt(root, "title", "properties.title.title.0.plain_text", "name");
            default -> textAt(root, "title", "name", "fileName", "originalFilename", "summary");
        };
    }

    private String koreanType(RawActivityType type) {
        return switch (type) {
            case GITHUB_COMMIT -> "커밋";
            case GITHUB_PULL_REQUEST -> "PR";
            case GITHUB_PULL_REQUEST_REVIEW -> "PR 리뷰";
            case GITHUB_ISSUE -> "이슈";
            case GITHUB_ISSUE_COMMENT -> "이슈 댓글";
            case FIGMA_FILE_VERSION -> "버전";
            case FIGMA_COMMENT -> "댓글";
            case GOOGLE_DRIVE_ACTIVITY -> "문서 활동";
            case GOOGLE_DRIVE_REVISION -> "수정 이력";
            case GOOGLE_DRIVE_COMMENT -> "댓글";
            case NOTION_COMMENT -> "댓글";
            default -> "활동";
        };
    }

    private String commitSha(ReportActivityLog log) {
        return firstNonBlank(textAt(metadata(log.getMetadata()), "sha", "commit_sha", "commitSha"), log.getSourceRefId());
    }

    private JsonNode metadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            return root == null ? objectMapper.createObjectNode() : root;
        } catch (JsonProcessingException ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private String textAt(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode value = root;
            for (String segment : path.split("\\.")) {
                if (value == null || value.isMissingNode()) {
                    break;
                }
                value = segment.chars().allMatch(Character::isDigit)
                        ? value.path(Integer.parseInt(segment))
                        : value.path(segment);
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String sanitize(String source) {
        String noUrl = URL.matcher(source).replaceAll("[링크]");
        String noEmail = EMAIL.matcher(noUrl).replaceAll("[이메일]");
        String noLogin = LOGIN.matcher(noEmail).replaceAll("[계정]");
        return noLogin.length() > 140 ? noLogin.substring(0, 140) : noLogin;
    }

    private record EvidenceCandidate(
            SourceDomain domain,
            RawActivityType type,
            LinkType linkType,
            LocalDate occurredDate,
            BigDecimal weight,
            String text
    ) {
    }
}
