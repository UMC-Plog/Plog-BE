package com.plog.domain.post.service;

import com.plog.domain.post.dto.PostDto;
import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.entity.PostAttachment;
import com.plog.domain.post.exception.PostErrorCode;
import com.plog.domain.post.repository.PostAttachmentRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileDeletionEvent;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.FilePromotionEvent;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final ProjectRepository projectRepository;
    private final ProjectAccessService projectAccessService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PostDto.Response createPost(Long projectId, Long userId, PostDto.CreateRequest request) {
        requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        String content = requireContent(request.content(), 5000);
        List<PostDto.AttachmentRequest> attachments = safeAttachments(request.attachments());
        validateAttachments(userId, attachments);
        Post post = postRepository.saveAndFlush(Post.builder()
                .projectMember(member).content(content).isNotice(false).build());
        List<PostAttachment> savedAttachments = saveAttachments(post, attachments);
        publishPromotions(savedAttachments);
        return toResponse(post, member, savedAttachments);
    }

    public PostDto.Response getPost(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        return toResponse(post, member, attachmentRepository.findAllByPostIdOrderByIdAsc(postId));
    }

    public PostDto.FeedResponse getFeed(Long projectId, Long userId, String cursor, int requestedSize) {
        requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        int size = requestedSize <= 0 ? 20 : Math.min(requestedSize, 50);
        Cursor decoded = decodeCursor(cursor);
        List<Post> fetched = postRepository.findFeedPage(
                projectId, decoded == null ? null : decoded.createdAt(), decoded == null ? null : decoded.postId(),
                PageRequest.of(0, size + 1));
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
        return new PostDto.FeedResponse(posts, nextCursor, hasNext);
    }

    @Transactional
    public PostDto.Response updatePost(Long projectId, Long postId, Long userId, PostDto.UpdateRequest request) {
        requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
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
            if (!removedFileKeys.isEmpty()) {
                eventPublisher.publishEvent(new FileDeletionEvent(removedFileKeys));
            }
        } else {
            resultingAttachments = attachmentRepository.findAllByPostIdOrderByIdAsc(postId);
        }
        postRepository.saveAndFlush(post);
        return toResponse(post, member, resultingAttachments);
    }

    @Transactional
    public PostDto.DeletedResponse deletePost(Long projectId, Long postId, Long userId) {
        requireProject(projectId);
        ProjectMember member = projectAccessService.requireActiveMember(projectId, userId);
        Post post = requirePost(projectId, postId);
        if (!post.getProjectMember().getId().equals(member.getId())) {
            throw new ApiException(PostErrorCode.POST_DELETE_PERMISSION_DENIED);
        }
        List<String> fileKeys = attachmentRepository.findAllByPostIdOrderByIdAsc(postId).stream()
                .filter(item -> item.getAttachmentType() == AttachmentType.FILE)
                .map(PostAttachment::getFileUrl).toList();
        postRepository.delete(post);
        postRepository.flush();
        if (!fileKeys.isEmpty()) {
            eventPublisher.publishEvent(new FileDeletionEvent(fileKeys));
        }
        return new PostDto.DeletedResponse(true);
    }

    private PostDto.Response toResponse(Post post, ProjectMember viewer, List<PostAttachment> attachments) {
        List<PostDto.AttachmentResponse> attachmentResponses = attachments.stream().map(this::toAttachmentResponse).toList();
        return new PostDto.Response(
                post.getId(), post.getProjectMember().getProject().getId(), post.getProjectMember().getId(),
                post.getProjectMember().getAnNickname(), post.getContent(),
                0, 0, false, attachmentResponses,
                toInstant(post.getCreatedAt()), toInstant(post.getUpdatedAt()));
    }

    private PostDto.AttachmentResponse toAttachmentResponse(PostAttachment attachment) {
        String url = attachment.getAttachmentType() == AttachmentType.FILE
                ? fileStorageService.createDownloadUrl(attachment.getFileUrl()) : attachment.getFileUrl();
        return new PostDto.AttachmentResponse(
                attachment.getId(), attachment.getAttachmentType(), attachment.getFileName(), attachment.getFileSize(), url);
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

    private void validateAttachments(Long userId, List<PostDto.AttachmentRequest> requests) {
        if (requests.size() > 10) {
            throw new ApiException(PostErrorCode.VALIDATION_ERROR);
        }
        for (PostDto.AttachmentRequest request : requests) {
            if (request == null || request.attachmentType() == null || request.attachmentType() == AttachmentType.EXTERNAL) {
                throw new ApiException(PostErrorCode.VALIDATION_ERROR);
            }
            if (request.attachmentType() == AttachmentType.FILE) {
                if (request.fileName() == null || request.fileSize() == null || request.fileKey() == null) {
                    throw new ApiException(PostErrorCode.VALIDATION_ERROR);
                }
                fileStorageService.verifyUploadedFile(userId, request.fileKey(), request.fileName(), request.fileSize());
            } else {
                validateLink(request.fileUrl());
            }
        }
    }

    private void validateLink(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || host.equalsIgnoreCase("localhost") || isPrivateLiteral(host)) {
                throw new ApiException(PostErrorCode.INVALID_LINK_URL);
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(PostErrorCode.INVALID_LINK_URL, exception);
        }
    }

    private boolean isPrivateLiteral(String host) {
        String normalized = host.toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("^(10\\.|127\\.|169\\.254\\.|192\\.168\\.|0\\.)")
                || normalized.equals("::1") || normalized.startsWith("fc")
                || normalized.startsWith("fd") || normalized.startsWith("fe80:");
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

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
    }

    private Post requirePost(Long projectId, Long postId) {
        return postRepository.findByIdAndProjectMemberProjectId(postId, projectId)
                .orElseThrow(() -> new ApiException(PostErrorCode.POST_NOT_FOUND));
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
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
