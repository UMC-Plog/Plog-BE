package com.plog.domain.report.service;

import com.plog.domain.post.entity.Comment;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.repository.CommentRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.project.entity.ProjectMember;
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
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectPostCreated(Long postId, Long memberId, String content, LocalDateTime occurredAt) {
        String sourceRefId = "post:" + postId;
        activityLogRepository.acquireSourceLock(SourceDomain.POST.name() + ":" + sourceRefId);
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            activityLogRepository.deleteBySourceDomainAndSourceRefId(SourceDomain.POST, sourceRefId);
            return;
        }
        collect(post.getProjectMember(), RawActivityType.POST_CREATE, post.getContent(), occurredAt,
                sourceRefId, "{\"postId\":" + postId + "}");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectCommentCreated(
            Long commentId, Long postId, Long memberId, String content, LocalDateTime occurredAt
    ) {
        String sourceRefId = "comment:" + commentId;
        activityLogRepository.acquireSourceLock(SourceDomain.POST.name() + ":" + sourceRefId);
        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            activityLogRepository.deleteBySourceDomainAndSourceRefId(SourceDomain.POST, sourceRefId);
            return;
        }
        Long currentPostId = comment.getPost().getId();
        collect(comment.getProjectMember(), RawActivityType.COMMENT_CREATE, comment.getContent(), occurredAt,
                sourceRefId,
                "{\"postId\":" + currentPostId + ",\"commentId\":" + commentId + "}");
    }

    private void collect(
            ProjectMember member,
            RawActivityType activityType,
            String content,
            LocalDateTime occurredAt,
            String sourceRefId,
            String metadata
    ) {
        if (activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.POST, sourceRefId)) {
            return;
        }
        activityLogRepository.save(ReportActivityLog.create(
                member, SourceDomain.POST, activityType, content, occurredAt, metadata, sourceRefId));
    }
}
