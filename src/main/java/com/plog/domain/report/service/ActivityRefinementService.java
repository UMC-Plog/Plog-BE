package com.plog.domain.report.service;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리포트 파이프라인 1단계(Rule 기반 정제) 적용. {@link ActivityContentRefiner}로 계산한 규칙 결과에
 * 회원 단위 중복 메시지 판정을 더해 {@link ReportActivityLog#applyNoiseFilter}를 확정한다.
 * <p>
 * 정제 대상은 TASK/CHAT/POST(내부 도메인)뿐이다 — 외부 연동은 rawActivityType이 이미 세분류라
 * 애초에 조회 대상에 들어오지 않는다({@link ReportActivityLogRepository#findBySourceDomainInAndNoiseFilteredIsNullOrderByOccurredAtAscIdAsc}).
 * <p>
 * 중복 판정은 두 겹으로 이뤄진다: (1) 이번 배치 안에서 이미 나온 동일 정제 내용인지, (2) 이전 배치에서
 * 이미 noiseFiltered=false로 확정된 동일 원문이 DB에 있는지. (1)만으로는 500건 배치 경계를 넘는
 * 중복(같은 메시지가 서로 다른 호출에서 처리되는 경우)을 놓치기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityRefinementService {

    private static final List<SourceDomain> REFINABLE_DOMAINS =
            List.of(SourceDomain.TASK, SourceDomain.CHAT, SourceDomain.POST);

    // 한 배치에서 처리할 상한. EvaluationActivityLogRecoveryScheduler의 관례를 따른다.
    private static final Limit BATCH_LIMIT = Limit.of(500);

    private final ReportActivityLogRepository activityLogRepository;

    /**
     * 아직 정제되지 않은 활동 로그를 배치로 가져와 노이즈 여부를 확정한다.
     *
     * @return 이번 호출에서 정제(noiseFiltered 확정)한 건수
     */
    @Transactional
    public int refineNoiseBatch() {
        List<ReportActivityLog> targets = activityLogRepository
                .findBySourceDomainInAndNoiseFilteredIsNullOrderByOccurredAtAscIdAsc(REFINABLE_DOMAINS, BATCH_LIMIT);
        if (targets.isEmpty()) {
            return 0;
        }

        // 쿼리가 이미 occurredAt/id 오름차순으로 정렬해 주므로 "중복 중 가장 먼저 온 것"만
        // 자연스럽게 살아남는다 — 별도 Java 정렬이 필요 없다.
        Set<String> seenDedupeKeysInBatch = new HashSet<>();
        for (ReportActivityLog activity : targets) {
            RefinedContent refined = ActivityContentRefiner.refine(activity.getContent());
            boolean noise = refined.noise() || isDuplicate(activity, refined, seenDedupeKeysInBatch);
            activity.applyNoiseFilter(noise);
        }

        log.info("activity_refinement_batch_applied count={} domains={}", targets.size(), REFINABLE_DOMAINS);
        return targets.size();
    }

    /**
     * 같은 회원·같은 도메인에서 정제 후 내용이 완전히 같은 메시지가 이미 나왔는지 확인한다.
     * cleanContent가 없는 행(텍스트 없는 이벤트)은 애초에 비교 대상이 아니다 — 여러 상태변경 이벤트를
     * 서로 "중복"으로 잘못 묶지 않기 위해서다.
     * <p>
     * projectMember가 없는 행(외부 계정 매핑이 안 된 시점에 수집된 활동)은 서로 다른 실제 주체일 수
     * 있어 중복 판정에서 완전히 제외한다 — null을 키에 그대로 넣으면 서로 무관한 로그가 같은
     * dedupe key로 묶여 두 번째 이후 로그가 잘못 노이즈 처리될 수 있다.
     */
    private boolean isDuplicate(ReportActivityLog activity, RefinedContent refined, Set<String> seenDedupeKeysInBatch) {
        if (!refined.hasCleanContent()) {
            return false;
        }
        ProjectMember member = activity.getProjectMember();
        if (member == null) {
            return false;
        }

        String dedupeKey = activity.getSourceDomain() + ":" + member.getId() + ":" + refined.cleanContent();
        if (!seenDedupeKeysInBatch.add(dedupeKey)) {
            return true; // 이번 배치 안에서 이미 나온 동일 메시지
        }

        // 배치 경계를 넘는 중복: 더 이전에 이미 노이즈 아님으로 확정된 동일 원문이 있는지 DB로 확인.
        return activityLogRepository.existsByProjectMember_IdAndSourceDomainAndContentAndNoiseFilteredFalseAndIdLessThan(
                member.getId(), activity.getSourceDomain(), activity.getContent(), activity.getId());
    }
}