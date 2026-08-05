package com.plog.domain.report.repository;

import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportActivityLogRepository extends JpaRepository<ReportActivityLog, Long> {

    boolean existsBySourceDomainAndSourceRefId(SourceDomain sourceDomain, String sourceRefId);

    // 1단계 정제 대상 조회용 — 아직 정제를 거치지 않은 내부 도메인 행
    List<ReportActivityLog> findBySourceDomainInAndNoiseFilteredIsNull(List<SourceDomain> sourceDomains);

    // 4단계 정량계산에서 멤버별로 묶어서 집계할 때 사용
    List<ReportActivityLog> findByProjectMember_IdAndSourceDomainIn(
            Long projectMemberId, List<SourceDomain> sourceDomains);
}
