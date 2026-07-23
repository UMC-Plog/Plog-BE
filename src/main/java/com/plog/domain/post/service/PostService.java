package com.plog.domain.post.service;

import com.plog.domain.post.dto.CommentDto;
import com.plog.domain.post.dto.PostDto;
import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.Comment;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.entity.PostAttachment;
import com.plog.domain.post.exception.PostErrorCode;
import com.plog.domain.post.repository.CommentRepository;
import com.plog.domain.post.repository.PostAttachmentRepository;
import com.plog.domain.post.repository.PostLikeRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.PostFileDeletionEvent;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.FilePromotionEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final PostAttachmentRepository attachmentRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final FileStorageService fileStorageService;
    private final AttachmentPolicy attachmentPolicy;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PostDto.CreateResponse createPost(Long projectId, Long userId, PostDto.CreateRequest request) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        if (request.isNotice() && member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(PostErrorCode.NOTICE_PERMISSION_DENIED);
        }
        String content = requireContent(request.content(), 5000);
        List<PostDto.AttachmentRequest> attachments = safeAttachments(request.attachments());
        validateAttachments(userId, attachments);
        if (request.isNotice()) {
            projectRepository.findByIdForUpdate(projectId)
                    .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND));
            clearNotices(projectId, null);
        }
        Post post = postRepository.saveAndFlush(Post.builder()
                .projectMember(member).content(content).isNotice(request.isNotice()).build());
        List<PostAttachment> savedAttachments = saveAttachments(post, attachments);
        publishPromotions(savedAttachments);
        return toCreateResponse(post, member, savedAttachments);
    }

    public PostDto.Response getPost(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        return toResponse(post, member, attachmentRepository.findAllByPostIdOrderByIdAsc(postId));
    }

    public PostDto.FeedResponse getFeed(Long projectId, Long userId, String cursor, int requestedSize) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        if (requestedSize <= 0) {
            throw new ApiException(PostErrorCode.VALIDATION_ERROR);
        }
        int size = Math.min(requestedSize, 50);
        Cursor decoded = decodeCursor(cursor);
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<Post> fetched = decoded == null
                ? postRepository.findFirstFeedPage(projectId, pageRequest)
                : postRepository.findFeedPageAfter(
                        projectId, decoded.createdAt(), decoded.postId(), pageRequest);
        boolean hasNext = fetched.size() > size;
        List<Post> page = hasNext ? fetched.subList(0, size) : fetched;
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, List<PostAttachment>> attachmentsByPostId = postIds.isEmpty()
                ? Map.of()
                : attachmentRepository.findAllByPostIdInOrderByIdAsc(postIds).stream()
                        .collect(Collectors.groupingBy(attachment -> attachment.getPost().getId()));
        List<PostDto.Response> posts = page.stream()
                .map(post -> toResponse(post, member, attachmentsByPostId.getOrDefault(post.getId(), List.of())))
                .toList();
        String nextCursor = hasNext && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1)) : null;
        PostDto.Response notice = postRepository.findFirstByProjectMemberProjectIdAndIsNoticeTrue(projectId)
                .map(post -> toResponse(post, member, attachmentRepository.findAllByPostIdOrderByIdAsc(post.getId())))
                .orElse(null);
        return new PostDto.FeedResponse(notice, posts, nextCursor, hasNext);
    }

    @Transactional
    public PostDto.UpdateResponse updatePost(Long projectId, Long postId, Long userId, PostDto.UpdateRequest request) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        if (!post.getProjectMember().getId().equals(member.getId())) {
            throw new ApiException(PostErrorCode.POST_UPDATE_PERMISSION_DENIED);
        }
        if (request.content() != null) {
            post.updateContent(requireContent(request.content(), 5000));
        }
        List<PostAttachment> resultingAttachments;
        if (request.attachments() != null) {
            validateAttachments(userId, request.attachments());
            List<PostAttachment> previous = attachmentRepository.findAllByPostIdOrderByIdAsc(postId);
            List<String> nextFileKeys = request.attachments().stream()
                    .filter(item -> item.attachmentType() == AttachmentType.FILE)
                    .map(PostDto.AttachmentRequest::fileKey).toList();
            List<String> removedFileKeys = previous.stream()
                    .filter(item -> item.getAttachmentType() == AttachmentType.FILE)
                    .map(PostAttachment::getFileUrl)
                    .filter(fileKey -> !nextFileKeys.contains(fileKey)).toList();
            attachmentRepository.deleteAllByPostId(postId);
            attachmentRepository.flush();
            resultingAttachments = saveAttachments(post, request.attachments());
            publishPromotions(resultingAttachments);
            publishPostFileDeletionCandidates(removedFileKeys);
        } else {
            resultingAttachments = attachmentRepository.findAllByPostIdOrderByIdAsc(postId);
        }
        postRepository.saveAndFlush(post);
        return toUpdateResponse(post, member, resultingAttachments);
    }

    @Transactional
    public PostDto.DeletedResponse deletePost(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        if (!post.getProjectMember().getId().equals(member.getId()) && member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(PostErrorCode.POST_DELETE_PERMISSION_DENIED);
        }
        List<String> fileKeys = attachmentRepository.findAllByPostIdOrderByIdAsc(postId).stream()
                .filter(item -> item.getAttachmentType() == AttachmentType.FILE)
                .map(PostAttachment::getFileUrl).toList();
        postRepository.delete(post);
        postRepository.flush();
        publishPostFileDeletionCandidates(fileKeys);
        return new PostDto.DeletedResponse(true);
    }

    @Transactional
    public PostDto.NoticeResponse changeNotice(
            Long projectId, Long postId, Long userId, PostDto.NoticeRequest request
    ) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(PostErrorCode.NOTICE_PERMISSION_DENIED);
        }
        projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND));
        Post post = requirePost(projectId, postId);
        if (Boolean.TRUE.equals(request.isNotice())) {
            clearNotices(projectId, postId);
        }
        post.changeNotice(Boolean.TRUE.equals(request.isNotice()));
        postRepository.saveAndFlush(post);
        return new PostDto.NoticeResponse(post.getId(), projectId, post.isNotice(), toInstant(post.getUpdatedAt()));
    }

    @Transactional
    public CommentDto.Response createComment(
            Long projectId, Long postId, Long userId, CommentDto.CreateRequest request
    ) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        String content = requireContent(request.content(), 1000);
        Comment comment = commentRepository.saveAndFlush(Comment.builder()
                .post(post).projectMember(member).content(content).build());
        return toCommentResponse(comment);
    }

    public CommentDto.ListResponse getComments(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        requireActiveMember(projectId, userId);
        requirePost(projectId, postId);
        return new CommentDto.ListResponse(postId,
                commentRepository.findAllByPostIdOrderByCreatedAtAscIdAsc(postId).stream()
                        .map(this::toCommentResponse).toList());
    }

    @Transactional
    public PostDto.DeletedResponse deleteComment(
            Long projectId, Long postId, Long commentId, Long userId
    ) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        requirePost(projectId, postId);
        Comment comment = commentRepository.findByIdAndPostId(commentId, postId)
                .orElseThrow(() -> new ApiException(PostErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getProjectMember().getId().equals(member.getId()) && member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(PostErrorCode.COMMENT_DELETE_PERMISSION_DENIED);
        }
        commentRepository.delete(comment);
        return new PostDto.DeletedResponse(true);
    }

    @Transactional
    public PostDto.LikeResponse like(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        postLikeRepository.insertIgnore(post.getId(), member.getId());
        return new PostDto.LikeResponse(postId, true, postLikeRepository.countByPostId(postId));
    }

    @Transactional
    public PostDto.LikeResponse unlike(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        requirePost(projectId, postId);
        postLikeRepository.findByPostIdAndProjectMemberId(postId, member.getId())
                .ifPresent(postLikeRepository::delete);
        postLikeRepository.flush();
        return new PostDto.LikeResponse(postId, false, postLikeRepository.countByPostId(postId));
    }

    private PostDto.Response toResponse(Post post, ProjectMember viewer, List<PostAttachment> attachments) {
        List<PostDto.AttachmentResponse> attachmentResponses = attachments.stream().map(this::toAttachmentResponse).toList();
        return new PostDto.Response(
                post.getId(), post.getProjectMember().getProject().getId(), post.getProjectMember().getId(),
                post.getProjectMember().getAnNickname(), post.getContent(), post.isNotice(),
                postLikeRepository.countByPostId(post.getId()), commentRepository.countByPostId(post.getId()),
                postLikeRepository.existsByPostIdAndProjectMemberId(post.getId(), viewer.getId()), attachmentResponses,
                toInstant(post.getCreatedAt()), toInstant(post.getUpdatedAt()));
    }

    private PostDto.CreateResponse toCreateResponse(
            Post post,
            ProjectMember viewer,
            List<PostAttachment> attachments
    ) {
        return new PostDto.CreateResponse(
                post.getId(), post.getProjectMember().getProject().getId(), post.getProjectMember().getId(),
                post.getContent(), post.isNotice(), postLikeRepository.countByPostId(post.getId()),
                commentRepository.countByPostId(post.getId()),
                postLikeRepository.existsByPostIdAndProjectMemberId(post.getId(), viewer.getId()),
                attachments.stream().map(this::toAttachmentResponse).toList(),
                toInstant(post.getCreatedAt()));
    }

    private PostDto.UpdateResponse toUpdateResponse(
            Post post,
            ProjectMember viewer,
            List<PostAttachment> attachments
    ) {
        return new PostDto.UpdateResponse(
                post.getId(), post.getProjectMember().getProject().getId(), post.getProjectMember().getId(),
                post.getContent(), post.isNotice(), postLikeRepository.countByPostId(post.getId()),
                commentRepository.countByPostId(post.getId()),
                postLikeRepository.existsByPostIdAndProjectMemberId(post.getId(), viewer.getId()),
                attachments.stream().map(this::toAttachmentResponse).toList(),
                toInstant(post.getUpdatedAt()));
    }

    private PostDto.AttachmentResponse toAttachmentResponse(PostAttachment attachment) {
        String url = attachment.getAttachmentType() == AttachmentType.FILE
                ? fileStorageService.createDownloadUrl(
                        AttachmentUsage.POST, attachment.getFileUrl(), attachment.getFileName())
                : attachment.getFileUrl();
        return new PostDto.AttachmentResponse(
                attachment.getId(), attachment.getAttachmentType(), attachment.getFileName(), attachment.getFileSize(), url);
    }

    private CommentDto.Response toCommentResponse(Comment comment) {
        return new CommentDto.Response(
                comment.getId(), comment.getPost().getId(),
                comment.getPost().getProjectMember().getProject().getId(), comment.getProjectMember().getId(),
                comment.getProjectMember().getAnNickname(), comment.getContent(), toInstant(comment.getCreatedAt()));
    }

    private List<PostAttachment> saveAttachments(Post post, List<PostDto.AttachmentRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<PostAttachment> attachments = requests.stream().map(request -> {
            String storedUrl = request.attachmentType() == AttachmentType.FILE ? request.fileKey() : request.fileUrl();
            return PostAttachment.builder().post(post).attachmentType(request.attachmentType())
                    .fileName(request.fileName()).fileSize(request.fileSize()).fileUrl(storedUrl).build();
        }).toList();
        return attachmentRepository.saveAllAndFlush(attachments);
    }

    private void publishPromotions(List<PostAttachment> attachments) {
        List<String> fileKeys = attachments.stream()
                .filter(item -> item.getAttachmentType() == AttachmentType.FILE)
                .map(PostAttachment::getFileUrl).toList();
        if (!fileKeys.isEmpty()) {
            eventPublisher.publishEvent(new FilePromotionEvent(fileKeys));
        }
    }

    private void publishPostFileDeletionCandidates(List<String> candidateFileKeys) {
        List<String> distinctFileKeys = candidateFileKeys.stream().distinct().toList();
        if (!distinctFileKeys.isEmpty()) {
            eventPublisher.publishEvent(new PostFileDeletionEvent(distinctFileKeys));
        }
    }

    private void validateAttachments(Long userId, List<PostDto.AttachmentRequest> requests) {
        attachmentPolicy.validateCount(requests.size(), PostErrorCode.VALIDATION_ERROR);
        for (PostDto.AttachmentRequest request : requests) {
            if (request == null || request.attachmentType() == null
                    || request.attachmentType() == AttachmentType.EXTERNAL) {
                throw new ApiException(PostErrorCode.VALIDATION_ERROR);
            }
            if (request.attachmentType() == AttachmentType.FILE) {
                attachmentPolicy.validateFileAttachment(AttachmentUsage.POST, userId,
                        request.fileName(), request.fileSize(), request.fileKey(),
                        PostErrorCode.VALIDATION_ERROR);
            } else {
                attachmentPolicy.validateLink(request.fileUrl(), PostErrorCode.INVALID_LINK_URL);
            }
        }
    }


    private String requireContent(String content, int maxLength) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            throw new ApiException(PostErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }

    private List<PostDto.AttachmentRequest> safeAttachments(List<PostDto.AttachmentRequest> attachments) {
        return attachments == null ? List.of() : attachments;
    }

    private void clearNotices(Long projectId, Long exceptPostId) {
        List<Post> notices = new ArrayList<>(postRepository.findAllByProjectMemberProjectIdAndIsNoticeTrue(projectId));
        notices.stream().filter(post -> !post.getId().equals(exceptPostId)).forEach(post -> post.changeNotice(false));
        postRepository.saveAll(notices);
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private ProjectMember requireActiveMember(Long projectId, Long userId) {
        if (userId == null) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED);
        }
        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED));
        if (member.getRole() != ProjectRole.OWNER && member.getRole() != ProjectRole.MEMBER) {
            throw new ApiException(ProjectApiErrorCode.PROJECT_MEMBER_REQUIRED);
        }
        return member;
    }

    private Post requirePost(Long projectId, Long postId) {
        return postRepository.findByIdAndProjectMemberProjectId(postId, projectId)
                .orElseThrow(() -> new ApiException(PostErrorCode.POST_NOT_FOUND));
    }

    private Instant toInstant(LocalDateTime value) {
        return TimeUtil.toInstant(value);
    }

    private String encodeCursor(Post post) {
        String raw = toInstant(post.getCreatedAt()) + "|" + post.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(LocalDateTime.ofInstant(Instant.parse(parts[0]), ZoneOffset.UTC), Long.valueOf(parts[1]));
        } catch (RuntimeException exception) {
            throw new ApiException(PostErrorCode.INVALID_CURSOR, exception);
        }
    }

    private record Cursor(LocalDateTime createdAt, Long postId) {}
}
