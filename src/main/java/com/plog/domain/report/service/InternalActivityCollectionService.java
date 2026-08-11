package com.plog.domain.report.service;

import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.post.repository.CommentRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.domain.task.repository.TaskStatusHistoryRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalActivityCollectionService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskStatusHistoryRepository taskStatusHistoryRepository;
    private final ChatActivityLogService chatActivityLogService;
    private final PostActivityLogService postActivityLogService;
    private final TaskActivityLogService taskActivityLogService;

    @Transactional(readOnly = true)
    public void collectProject(Long projectId) {
        collectChats(projectId);
        collectPosts(projectId);
        collectTasks(projectId);
    }

    private void collectChats(Long projectId) {
        chatRoomRepository.findByProjectId(projectId).ifPresent(room ->
                chatMessageRepository.findAllByChatRoomIdOrderByCreatedAtAscIdAsc(room.getId())
                        .forEach(message -> chatActivityLogService.collectMessageSaved(message.getId())));
    }

    private void collectPosts(Long projectId) {
        postRepository.findAllByProjectMemberProjectIdOrderByCreatedAtAscIdAsc(projectId).forEach(post -> {
            postActivityLogService.collectPostCreated(
                    post.getId(), post.getProjectMember().getId(), post.getContent(), post.getCreatedAt());
            commentRepository.findAllByPostIdOrderByCreatedAtAscIdAsc(post.getId()).forEach(comment ->
                    postActivityLogService.collectCommentCreated(
                            comment.getId(), post.getId(), comment.getProjectMember().getId(),
                            comment.getContent(), comment.getCreatedAt()));
        });
    }

    private void collectTasks(Long projectId) {
        var histories = taskStatusHistoryRepository
                .findAllByTaskProjectMemberProjectIdOrderByOccurredAtAscIdAsc(projectId);
        histories.forEach(history -> taskActivityLogService.collectStatusChanged(
                history.getTask().getId(), history.getProjectMember().getId(),
                history.getPreviousStatus(), history.getNewStatus(), history.getOccurredAt()));
        Set<Long> tasksWithHistory = histories.stream()
                .map(history -> history.getTask().getId())
                .collect(Collectors.toSet());
        taskRepository.findAllByProjectMember_Project_IdOrderByCreatedAtAsc(projectId).forEach(task -> {
            if (!tasksWithHistory.contains(task.getId())
                    && task.getCardStatus() == TaskStatus.DONE && task.getCompletedAt() != null) {
                taskActivityLogService.collectStatusChanged(
                        task.getId(), task.getProjectMember().getId(), null,
                        TaskStatus.DONE, task.getCompletedAt());
            }
            taskAttachmentRepository.findAllByTaskId(task.getId()).forEach(attachment ->
                    taskActivityLogService.collectAttachmentAdded(
                            attachment.getId(), task.getId(), task.getProjectMember().getId(),
                            attachment.getCreatedAt()));
        });
    }
}
