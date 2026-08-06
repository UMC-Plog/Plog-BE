package com.plog.domain.report.service;

import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.util.ArrayList;
import java.util.Comparator;
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
 * 애초에 조회 대상에 들어오지 않는다({@link ReportActivityLogRepository#findBySourceDomainInAndNoiseFilteredIsNull}).
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
        List<ReportActivityLog> fetched =
                activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(REFINABLE_DOMAINS, BATCH_LIMIT);
        if (fetched.isEmpty()) {
            return 0;
        }

        // 리포지토리가 불변 리스트를 돌려줄 수 있어 정렬 전에 가변 복사본을 만든다.
        // occurredAt 오름차순으로 처리해야 "중복 중 가장 먼저 온 것"만 살아남는다.
        List<ReportActivityLog> targets = new ArrayList<>(fetched);
        targets.sort(Comparator.comparing(ReportActivityLog::getOccurredAt));

        Set<String> seenDedupeKeys = new HashSet<>();
        for (ReportActivityLog activity : targets) {
            RefinedContent refined = ActivityContentRefiner.refine(activity.getContent());
            boolean noise = refined.noise() || isDuplicate(activity, refined, seenDedupeKeys);
            activity.applyNoiseFilter(noise);
        }

        log.info("activity_refinement_batch_applied count={} domains={}", targets.size(), REFINABLE_DOMAINS);
        return targets.size();
    }

    /**
     * 같은 회원·같은 도메인에서 정제 후 내용이 완전히 같은 메시지가 이미 나왔는지 확인한다.
     * cleanContent가 없는 행(텍스트 없는 이벤트)은 애초에 비교 대상이 아니다 — 여러 상태변경 이벤트를
     * 서로 "중복"으로 잘못 묶지 않기 위해서다.
     */
    private boolean isDuplicate(ReportActivityLog activity, RefinedContent refined, Set<String> seenDedupeKeys) {
        if (!refined.hasCleanContent()) {
            return false;
        }
        Long memberId = activity.getProjectMember() != null ? activity.getProjectMember().getId() : null;
        String dedupeKey = activity.getSourceDomain() + ":" + memberId + ":" + refined.cleanContent();
        return !seenDedupeKeys.add(dedupeKey);
    }
}