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
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
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
    private static final Pattern GITHUB_ITEM_URL = Pattern.compile("/(?:pulls?|issues)/(\\d+)(?:\\D|$)");

    private final ProjectIntegrationRepository projectIntegrationRepository;
    private final ProjectMemberIntegrationIdentityRepository identityRepository;
    private final ReportActivityLogRepository reportActivityLogRepository;
    private final ExternalActivityCompetencyMapper competencyMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ExternalReportData> provide(Long projectId, Collection<Long> projectMemberIds) {
        return provide(projectId, projectMemberIds, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ExternalReportData> provide(
            Long projectId, Collection<Long> projectMemberIds, LocalDateTime snapshotAt) {
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

        List<ReportActivityLog> logs = snapshotAt == null
                ? reportActivityLogRepository.findExternalLogsForActiveProjectMembers(
                        new ArrayList<>(mappedTypesByMember.keySet()), EXTERNAL_DOMAINS)
                : reportActivityLogRepository.findExternalLogsForActiveProjectMembersAt(
                        new ArrayList<>(mappedTypesByMember.keySet()), EXTERNAL_DOMAINS, snapshotAt);

        Map<Long, MemberAccumulator> accumulators = new LinkedHashMap<>();
        for (Map.Entry<Long, Set<LinkType>> entry : mappedTypesByMember.entrySet()) {
            accumulators.put(entry.getKey(), new MemberAccumulator(entry.getValue()));
        }
        EvidenceRelations evidenceRelations = new EvidenceRelations(
                preScanGithubRelationships(projectId, integrationsByType.keySet(), accumulators, logs),
                preScanNotionRelationships(logs)
        );
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
            accumulator.add(log, linkType, evidenceRelations);
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

    private GithubEvidenceRelations preScanGithubRelationships(
            Long projectId,
            Set<LinkType> activeTypes,
            Map<Long, MemberAccumulator> accumulators,
            List<ReportActivityLog> logs
    ) {
        Map<String, List<String>> parentsByCommit = new HashMap<>();
        for (ReportActivityLog log : logs) {
            if (log.getRawActivityType() != RawActivityType.GITHUB_COMMIT) {
                continue;
            }
            JsonNode root = metadata(log.getMetadata());
            String resourceKey = sourceResourceKey(log.getSourceRefId());
            String commitSha = commitSha(log, root);
            if (resourceKey.isBlank() || commitSha.isBlank()) {
                continue;
            }
            List<String> parentShas = new ArrayList<>();
            for (JsonNode parent : root.path("parents")) {
                String parentSha = parent.path("sha").asText("");
                if (!parentSha.isBlank()) {
                    parentShas.add(parentSha);
                }
            }
            parentsByCommit.put(commitGraphKey(resourceKey, commitSha), List.copyOf(parentShas));
        }

        Map<String, String> commitGroups = new HashMap<>();
        List<ReportActivityLog> pullRequests = logs.stream()
                .filter(log -> log.getRawActivityType() == RawActivityType.GITHUB_PULL_REQUEST)
                .sorted(Comparator.comparing(ReportActivityLog::getSourceRefId,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        for (ReportActivityLog log : pullRequests) {
            LinkType linkType = linkTypeFromSourceRef(projectId, log.getSourceRefId(), activeTypes);
            if (linkType == null) {
                continue;
            }
            JsonNode root = metadata(log.getMetadata());
            String resourceKey = sourceResourceKey(log.getSourceRefId());
            String groupKey = githubItemGroupKey(log, linkType, root);
            if (!resourceKey.isBlank() && !groupKey.isBlank()) {
                String mergeCommitSha = scalarAt(root, "merge_commit_sha", "mergeCommitSha");
                if (!mergeCommitSha.isBlank()) {
                    commitGroups.putIfAbsent(commitGraphKey(resourceKey, mergeCommitSha), groupKey);
                }
                mapPullRequestCommitAncestry(
                        resourceKey,
                        scalarAt(root, "head.sha", "headSha"),
                        scalarAt(root, "base.sha", "baseSha"),
                        groupKey,
                        parentsByCommit,
                        commitGroups
                );
            }

            if (log.getProjectMember() == null) {
                continue;
            }
            MemberAccumulator accumulator = accumulators.get(log.getProjectMember().getId());
            if (accumulator == null || !accumulator.mappedLinkTypes.contains(linkType)) {
                continue;
            }
            String mergeCommitSha = scalarAt(root, "merge_commit_sha", "mergeCommitSha");
            if (!mergeCommitSha.isBlank()) {
                accumulator.mergeCommitShas.add(mergeCommitSha);
            }
        }
        return new GithubEvidenceRelations(commitGroups);
    }

    private void mapPullRequestCommitAncestry(
            String resourceKey,
            String headSha,
            String baseSha,
            String groupKey,
            Map<String, List<String>> parentsByCommit,
            Map<String, String> commitGroups
    ) {
        if (headSha.isBlank() || baseSha.isBlank()) {
            return;
        }
        Deque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        boolean reachedBase = false;
        pending.add(headSha);
        while (!pending.isEmpty()) {
            String currentSha = pending.removeFirst();
            if (currentSha.isBlank() || !visited.add(currentSha)) {
                continue;
            }
            if (currentSha.equals(baseSha)) {
                reachedBase = true;
                continue;
            }
            String graphKey = commitGraphKey(resourceKey, currentSha);
            pending.addAll(parentsByCommit.getOrDefault(graphKey, List.of()));
        }
        if (reachedBase) {
            visited.stream()
                    .filter(commitSha -> !commitSha.equals(baseSha))
                    .forEach(commitSha -> commitGroups.putIfAbsent(
                            commitGraphKey(resourceKey, commitSha), groupKey));
        }
    }

    private String commitGraphKey(String resourceKey, String commitSha) {
        return resourceKey + ":" + commitSha;
    }

    private NotionEvidenceRelations preScanNotionRelationships(List<ReportActivityLog> logs) {
        Map<String, String> directPageByBlock = new HashMap<>();
        Map<String, String> parentBlockByBlock = new HashMap<>();
        Map<String, Set<String>> pageIdsByResource = new HashMap<>();

        for (ReportActivityLog log : logs) {
            JsonNode root = metadata(log.getMetadata());
            if (log.getRawActivityType() == RawActivityType.NOTION_PAGE_SNAPSHOT) {
                String pageId = scalarAt(root, "id");
                String resourceKey = sourceResourceKey(log.getSourceRefId());
                if (!pageId.isBlank() && !resourceKey.isBlank()) {
                    pageIdsByResource.computeIfAbsent(resourceKey, ignored -> new HashSet<>()).add(pageId);
                }
                if (!pageId.isBlank()) {
                    directPageByBlock.putIfAbsent(pageId, pageId);
                }
                continue;
            }
            if (log.getRawActivityType() != RawActivityType.NOTION_BLOCK_SNAPSHOT) {
                continue;
            }
            String blockId = scalarAt(root, "id");
            if (blockId.isBlank()) {
                continue;
            }
            String pageId = scalarAt(root, "parent.page_id");
            if (!pageId.isBlank()) {
                directPageByBlock.put(blockId, pageId);
            }
            String parentBlockId = scalarAt(root, "parent.block_id");
            if (!parentBlockId.isBlank()) {
                parentBlockByBlock.put(blockId, parentBlockId);
            }
        }

        Map<String, String> pageByBlock = new HashMap<>(directPageByBlock);
        for (String blockId : parentBlockByBlock.keySet()) {
            String pageId = resolveNotionPageId(blockId, directPageByBlock, parentBlockByBlock);
            if (!pageId.isBlank()) {
                pageByBlock.put(blockId, pageId);
            }
        }
        Map<String, String> uniquePageByResource = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : pageIdsByResource.entrySet()) {
            if (entry.getValue().size() == 1) {
                uniquePageByResource.put(entry.getKey(), entry.getValue().iterator().next());
            }
        }
        return new NotionEvidenceRelations(pageByBlock, uniquePageByResource);
    }

    private String resolveNotionPageId(
            String blockId,
            Map<String, String> directPageByBlock,
            Map<String, String> parentBlockByBlock
    ) {
        Set<String> visited = new HashSet<>();
        String currentBlockId = blockId;
        while (currentBlockId != null && visited.add(currentBlockId)) {
            String pageId = directPageByBlock.get(currentBlockId);
            if (pageId != null && !pageId.isBlank()) {
                return pageId;
            }
            currentBlockId = parentBlockByBlock.get(currentBlockId);
        }
        return "";
    }

    private final class MemberAccumulator {
        private final Set<LinkType> mappedLinkTypes;
        private final Map<SourceDomain, Long> countByDomain = new EnumMap<>(SourceDomain.class);
        private final Map<CompetencyCategory, Long> competencyCounts = new EnumMap<>(CompetencyCategory.class);
        private final Map<CompetencyCategory, List<EvidenceCandidate>> evidenceByCategory =
                new EnumMap<>(CompetencyCategory.class);
        private final Set<String> mergeCommitShas = new HashSet<>();
        private final Set<String> includedFigmaReactionDays = new HashSet<>();
        private BigDecimal rawScore = BigDecimal.ZERO;

        private MemberAccumulator(Set<LinkType> mappedLinkTypes) {
            this.mappedLinkTypes = mappedLinkTypes;
        }

        private void add(
                ReportActivityLog log,
                LinkType linkType,
                EvidenceRelations evidenceRelations
        ) {
            RawActivityType type = log.getRawActivityType();
            JsonNode root = metadata(log.getMetadata());
            if (isNotionEvidenceOnlySnapshot(type)) {
                addEvidenceCandidate(
                        CompetencyCategory.OUTPUT, log, linkType, root,
                        evidencePriorityOf(log), evidenceRelations);
                return;
            }

            BigDecimal weight = scoreWeightOf(log);
            if (type == RawActivityType.GITHUB_COMMIT && mergeCommitShas.contains(commitSha(log, root))) {
                weight = BigDecimal.ZERO;
            }
            boolean cappedFigmaReaction = false;
            if (type == RawActivityType.FIGMA_COMMENT_REACTION) {
                String reactionDayKey = linkType + ":" + log.getOccurredAt().toLocalDate();
                if (!includedFigmaReactionDays.add(reactionDayKey)) {
                    weight = BigDecimal.ZERO;
                    cappedFigmaReaction = true;
                }
            }

            countByDomain.merge(log.getSourceDomain(), 1L, Long::sum);
            rawScore = rawScore.add(weight);
            if (cappedFigmaReaction) {
                return;
            }

            Set<CompetencyCategory> categories = competencyMapper.map(type, log.getMetadata());
            for (CompetencyCategory category : categories) {
                competencyCounts.merge(category, 1L, Long::sum);
                if (type != RawActivityType.FIGMA_COMMENT_REACTION) {
                    addEvidenceCandidate(category, log, linkType, root, evidencePriorityOf(log), evidenceRelations);
                }
            }
        }

        private void addEvidenceCandidate(
                CompetencyCategory category,
                ReportActivityLog log,
                LinkType linkType,
                JsonNode root,
                BigDecimal weight,
                EvidenceRelations evidenceRelations
        ) {
            evidenceByCategory
                    .computeIfAbsent(category, ignored -> new ArrayList<>())
                    .add(new EvidenceCandidate(
                            log.getSourceDomain(), log.getRawActivityType(), linkType,
                            log.getOccurredAt().toLocalDate(), weight,
                            evidenceGroupKey(log, linkType, root, evidenceRelations),
                            evidenceText(log, linkType, root)));
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

    private BigDecimal scoreWeightOf(ReportActivityLog log) {
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

    private BigDecimal evidencePriorityOf(ReportActivityLog log) {
        return switch (log.getRawActivityType()) {
            case NOTION_PAGE_SNAPSHOT, NOTION_BLOCK_SNAPSHOT -> new BigDecimal("3");
            case NOTION_DATA_SOURCE_SNAPSHOT -> new BigDecimal("2");
            case NOTION_COMMENT -> BigDecimal.ONE;
            default -> scoreWeightOf(log);
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

    private boolean isNotionEvidenceOnlySnapshot(RawActivityType type) {
        return type == RawActivityType.NOTION_DATA_SOURCE_SNAPSHOT
                || type == RawActivityType.NOTION_PAGE_SNAPSHOT
                || type == RawActivityType.NOTION_BLOCK_SNAPSHOT;
    }

    private List<String> selectEvidence(List<EvidenceCandidate> candidates) {
        Set<String> usedGroups = new HashSet<>();
        List<EvidenceCandidate> sorted = candidates.stream()
                .sorted(evidencePriority())
                .filter(candidate -> usedGroups.add(candidate.groupKey()))
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
                .comparing(EvidenceCandidate::priority).reversed()
                .thenComparing(EvidenceCandidate::domain)
                .thenComparing(EvidenceCandidate::type)
                .thenComparing(EvidenceCandidate::occurredDate, Comparator.reverseOrder())
                .thenComparing(EvidenceCandidate::text);
    }

    private String evidenceText(ReportActivityLog log, LinkType linkType, JsonNode root) {
        String label = firstNonBlank(
                typeSpecificSummary(log, root),
                log.getRawActivityType().name());
        return sanitize("%s: %s %s".formatted(linkType.name(), koreanType(log.getRawActivityType()), label)
                .replaceAll("\\s+", " ")
                .trim());
    }

    private String typeSpecificSummary(ReportActivityLog log, JsonNode root) {
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
            case NOTION_PAGE_SNAPSHOT -> notionPageSummary(root);
            case NOTION_BLOCK_SNAPSHOT -> notionBlockSummary(root);
            case NOTION_DATA_SOURCE_SNAPSHOT -> textAt(root, "title.0.plain_text", "title", "name");
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
            case NOTION_DATA_SOURCE_SNAPSHOT -> "데이터소스 편집";
            case NOTION_PAGE_SNAPSHOT -> "페이지 편집";
            case NOTION_BLOCK_SNAPSHOT -> "블록 편집";
            default -> "활동";
        };
    }

    private String evidenceGroupKey(
            ReportActivityLog log,
            LinkType linkType,
            JsonNode root,
            EvidenceRelations evidenceRelations
    ) {
        String resourceKey = sourceResourceKey(log.getSourceRefId());
        return switch (log.getRawActivityType()) {
            case GITHUB_PULL_REQUEST, GITHUB_PULL_REQUEST_REVIEW, GITHUB_ISSUE, GITHUB_ISSUE_COMMENT ->
                    firstNonBlank(githubItemGroupKey(log, linkType, root), log.getSourceRefId());
            case GITHUB_COMMIT -> evidenceRelations.github().groupFor(resourceKey, commitSha(log, root))
                    .orElseGet(() -> "%s:commit:%s:%s".formatted(
                            linkType.name(), resourceKey, commitSha(log, root)));
            case FIGMA_FILE_VERSION, FIGMA_COMMENT -> "%s:file:%s".formatted(linkType.name(), resourceKey);
            case GOOGLE_DRIVE_ACTIVITY, GOOGLE_DRIVE_REVISION, GOOGLE_DRIVE_COMMENT ->
                    "%s:document:%s".formatted(linkType.name(), resourceKey);
            case NOTION_PAGE_SNAPSHOT -> "NOTION:page:" + firstNonBlank(scalarAt(root, "id"), resourceKey);
            case NOTION_BLOCK_SNAPSHOT -> "NOTION:page:" + notionBlockPageKey(
                    root, resourceKey, evidenceRelations.notion());
            case NOTION_DATA_SOURCE_SNAPSHOT -> "NOTION:data-source:" + firstNonBlank(
                    scalarAt(root, "id"), resourceKey);
            case NOTION_COMMENT -> "NOTION:page:" + notionCommentPageKey(
                    root, resourceKey, evidenceRelations.notion());
            default -> firstNonBlank(log.getSourceRefId(), linkType.name() + ":" + log.getRawActivityType());
        };
    }

    private String notionBlockPageKey(
            JsonNode root,
            String resourceKey,
            NotionEvidenceRelations notionRelations
    ) {
        return firstNonBlank(
                scalarAt(root, "parent.page_id"),
                notionRelations.pageForBlock(scalarAt(root, "id")).orElse(""),
                scalarAt(root, "parent.data_source_id", "parent.database_id"),
                notionRelations.pageForResource(resourceKey).orElse(""),
                scalarAt(root, "parent.block_id"),
                resourceKey
        );
    }

    private String notionCommentPageKey(
            JsonNode root,
            String resourceKey,
            NotionEvidenceRelations notionRelations
    ) {
        return firstNonBlank(
                scalarAt(root, "parent.page_id"),
                notionRelations.pageForBlock(scalarAt(root, "parent.block_id")).orElse(""),
                scalarAt(root, "parent.data_source_id", "parent.database_id"),
                notionRelations.pageForResource(resourceKey).orElse(""),
                scalarAt(root, "parent.block_id"),
                resourceKey
        );
    }

    private String githubItemGroupKey(ReportActivityLog log, LinkType linkType, JsonNode root) {
        String itemNumber = switch (log.getRawActivityType()) {
            case GITHUB_PULL_REQUEST, GITHUB_ISSUE -> scalarAt(root, "number");
            case GITHUB_PULL_REQUEST_REVIEW -> githubItemNumberFromUrls(
                    root, "pull_request_url", "_links.pull_request.href", "html_url");
            case GITHUB_ISSUE_COMMENT -> githubItemNumberFromUrls(root, "issue_url", "html_url");
            default -> "";
        };
        if (itemNumber.isBlank()) {
            return "";
        }
        return "%s:item:%s:%s".formatted(linkType.name(), sourceResourceKey(log.getSourceRefId()), itemNumber);
    }

    private String githubItemNumberFromUrl(String url) {
        var matcher = GITHUB_ITEM_URL.matcher(url);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String githubItemNumberFromUrls(JsonNode root, String... paths) {
        for (String path : paths) {
            String itemNumber = githubItemNumberFromUrl(scalarAt(root, path));
            if (!itemNumber.isBlank()) {
                return itemNumber;
            }
        }
        return "";
    }

    private String sourceResourceKey(String sourceRefId) {
        if (sourceRefId == null || sourceRefId.isBlank()) {
            return "";
        }
        String[] segments = sourceRefId.split(":", 6);
        return segments.length >= 5 && "integration".equals(segments[0])
                ? segments[3]
                : sourceRefId;
    }

    private String commitSha(ReportActivityLog log, JsonNode root) {
        return firstNonBlank(scalarAt(root, "sha", "commit_sha", "commitSha"), log.getSourceRefId());
    }

    private String notionPageSummary(JsonNode root) {
        String direct = textAt(root, "title", "name");
        if (!direct.isBlank()) {
            return direct;
        }
        JsonNode properties = root.path("properties");
        if (!properties.isObject()) {
            return "";
        }
        for (Map.Entry<String, JsonNode> field : properties.properties()) {
            JsonNode property = field.getValue();
            if ("title".equals(property.path("type").asText()) || property.path("title").isArray()) {
                String title = richText(property.path("title"));
                if (!title.isBlank()) {
                    return title;
                }
            }
        }
        return "";
    }

    private String notionBlockSummary(JsonNode root) {
        String type = root.path("type").asText("");
        JsonNode value = type.isBlank() ? root : root.path(type);
        return firstNonBlank(
                richText(value.path("rich_text")),
                scalarAt(value, "title", "caption.0.plain_text"),
                scalarAt(root, "title", "name")
        );
    }

    private String richText(JsonNode values) {
        if (!values.isArray()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode value : values) {
            String text = firstNonBlank(
                    scalarAt(value, "plain_text", "text.content"),
                    value.isTextual() ? value.asText() : "");
            if (!text.isBlank()) {
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(text);
            }
        }
        return result.toString();
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

    private String scalarAt(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode value = nodeAt(root, path);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private JsonNode nodeAt(JsonNode root, String path) {
        JsonNode value = root;
        for (String segment : path.split("\\.")) {
            if (value == null || value.isMissingNode()) {
                return null;
            }
            value = segment.chars().allMatch(Character::isDigit)
                    ? value.path(Integer.parseInt(segment))
                    : value.path(segment);
        }
        return value;
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
            BigDecimal priority,
            String groupKey,
            String text
    ) {
    }

    private record GithubEvidenceRelations(Map<String, String> commitGroups) {
        private GithubEvidenceRelations {
            commitGroups = Map.copyOf(commitGroups);
        }

        private java.util.Optional<String> groupFor(String resourceKey, String commitSha) {
            return java.util.Optional.ofNullable(commitGroups.get(resourceKey + ":" + commitSha));
        }
    }

    private record NotionEvidenceRelations(
            Map<String, String> pageByBlock,
            Map<String, String> uniquePageByResource
    ) {
        private NotionEvidenceRelations {
            pageByBlock = Map.copyOf(pageByBlock);
            uniquePageByResource = Map.copyOf(uniquePageByResource);
        }

        private java.util.Optional<String> pageForBlock(String blockId) {
            return java.util.Optional.ofNullable(pageByBlock.get(blockId));
        }

        private java.util.Optional<String> pageForResource(String resourceKey) {
            return java.util.Optional.ofNullable(uniquePageByResource.get(resourceKey));
        }
    }

    private record EvidenceRelations(
            GithubEvidenceRelations github,
            NotionEvidenceRelations notion
    ) {
    }
}
