package com.plog.domain.project.service;

import com.plog.infrastructure.s3.UploadedFileService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectPurgeService {

    private final UploadedFileService uploadedFileService;

    @PersistenceContext
    private EntityManager entityManager;

    public void purge(Long projectId) {
        // 벌크 delete 는 참조만 끊고 레지스트리 행을 남긴다. 먼저 해제해야
        // CONFIRMED 고아가 영구히 남지 않는다.
        uploadedFileService.release(collectFileIds(projectId));

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

    /** 세 도메인이 참조 중인 uploaded_file ID. 벌크 delete 전에 모아야 한다. */
    private List<Long> collectFileIds(Long projectId) {
        List<Long> fileIds = new ArrayList<>();
        fileIds.addAll(selectFileIds(
                "select a.uploadedFile.id from ChatAttachment a "
                        + "where a.chatMessage.chatRoom.project.id = :projectId", projectId));
        fileIds.addAll(selectFileIds(
                "select a.uploadedFile.id from TaskAttachment a "
                        + "where a.task.projectMember.project.id = :projectId "
                        + "and a.uploadedFile is not null", projectId));
        fileIds.addAll(selectFileIds(
                "select a.uploadedFile.id from PostAttachment a "
                        + "where a.post.projectMember.project.id = :projectId "
                        + "and a.uploadedFile is not null", projectId));
        return fileIds;
    }

    private List<Long> selectFileIds(String query, Long projectId) {
        return entityManager.createQuery(query, Long.class)
                .setParameter("projectId", projectId)
                .getResultList();
    }

    private void delete(String query, Long projectId) {
        entityManager.createQuery(query)
                .setParameter("projectId", projectId)
                .executeUpdate();
    }
}
