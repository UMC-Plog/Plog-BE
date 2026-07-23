package com.plog.domain.project.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

@Service
public class ProjectPurgeService {

    @PersistenceContext
    private EntityManager entityManager;

    public void purge(Long projectId) {
        delete("delete from ChatAttachment attachment where attachment.chatMessage.chatRoom.project.id = :projectId", projectId);
        delete("delete from ChatRoomReadCursor cursor where cursor.chatRoom.project.id = :projectId", projectId);
        delete("delete from ChatMessage message where message.chatRoom.project.id = :projectId", projectId);
        delete("delete from ChatRoom room where room.project.id = :projectId", projectId);
        delete("delete from ActivityLog activity where activity.resource.project.id = :projectId", projectId);
        delete("delete from ExternalResource resource where resource.project.id = :projectId", projectId);
        delete("delete from ExternalConnection connection where connection.projectMember.project.id = :projectId", projectId);
        delete("delete from TaskAttachment attachment where attachment.task.projectMember.project.id = :projectId", projectId);
        delete("delete from Task task where task.projectMember.project.id = :projectId", projectId);
        delete("delete from PostLike postLike where postLike.post.projectMember.project.id = :projectId", projectId);
        delete("delete from PostAttachment attachment where attachment.post.projectMember.project.id = :projectId", projectId);
        delete("delete from Comment comment where comment.post.projectMember.project.id = :projectId", projectId);
        delete("delete from Post post where post.projectMember.project.id = :projectId", projectId);
        delete("delete from PeerEvaluation evaluation where evaluation.evaluator.project.id = :projectId", projectId);
        delete("delete from SelfFeedback feedback where feedback.projectMember.project.id = :projectId", projectId);
        delete("delete from ReportMemberResult result where result.report.project.id = :projectId", projectId);
        delete("delete from Report report where report.project.id = :projectId", projectId);
        delete("delete from ProjectMember member where member.project.id = :projectId", projectId);
    }

    private void delete(String query, Long projectId) {
        entityManager.createQuery(query)
                .setParameter("projectId", projectId)
                .executeUpdate();
    }
}
