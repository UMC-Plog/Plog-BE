package com.plog.domain.report.service;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostActivityLogService {
    private final ReportActivityLogRepository activityLogRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectPostCreated(Long postId, Long memberId, String content, LocalDateTime occurredAt) {
        collect(memberId, RawActivityType.POST_CREATE, content, occurredAt,
                "post:" + postId, "{\"postId\":" + postId + "}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectCommentCreated(
            Long commentId, Long postId, Long memberId, String content, LocalDateTime occurredAt
    ) {
        collect(memberId, RawActivityType.COMMENT_CREATE, content, occurredAt,
                "comment:" + commentId,
                "{\"postId\":" + postId + ",\"commentId\":" + commentId + "}");
    }

    private void collect(
            Long memberId,
            RawActivityType activityType,
            String content,
            LocalDateTime occurredAt,
            String sourceRefId,
            String metadata
    ) {
        activityLogRepository.acquireSourceLock(SourceDomain.POST.name() + ":" + sourceRefId);
        if (activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.POST, sourceRefId)) {
            return;
        }
        ProjectMember member = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("활동 로그의 프로젝트 멤버를 찾을 수 없습니다."));
        activityLogRepository.save(ReportActivityLog.create(
                member, SourceDomain.POST, activityType, content, occurredAt, metadata, sourceRefId));
    }
}
