package com.plog.domain.post.service;

import com.plog.domain.notification.event.NoticePublishedEvent;
import com.plog.domain.post.dto.CommentDto;
import com.plog.domain.post.dto.PostDto;
import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.Comment;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.entity.PostAttachment;
import com.plog.domain.post.event.CommentCreatedEvent;
import com.plog.domain.post.event.PostCreatedEvent;
import com.plog.domain.post.exception.PostErrorCode;
import com.plog.domain.post.repository.CommentRepository;
import com.plog.domain.post.repository.PostAttachmentRepository;
import com.plog.domain.post.repository.PostLikeRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.global.common.AttachmentDownloadUrlFactory;
import com.plog.global.util.TimeUtil;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.UploadedFile;
import com.plog.infrastructure.s3.UploadedFileService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private final AttachmentDownloadUrlFactory downloadUrlFactory;
    private final AttachmentPolicy attachmentPolicy;
    private final UploadedFileService uploadedFileService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PostDto.CreateResponse createPost(Long projectId, Long userId, PostDto.CreateRequest request) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        String content = requireContent(request.content(), 5000);
        String title = requireContent(request.title(), 100);
        List<PostDto.AttachmentRequest> attachments = safeAttachments(request.attachments());
        List<UploadedFile> resolvedFiles = validateAttachments(userId, null, attachments);
        if (request.isNotice()) {
            projectRepository.findByIdForUpdate(projectId)
                    .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND));
            clearNotices(projectId, null);
        }
        Post post = postRepository.saveAndFlush(Post.builder()
                .projectMember(member).title(title).content(content).isNotice(request.isNotice())
                .noticedAt(request.isNotice() ? LocalDateTime.now(ZoneOffset.UTC) : null).build());
        List<PostAttachment> savedAttachments = saveAttachments(post, attachments, resolvedFiles);
        eventPublisher.publishEvent(new PostCreatedEvent(
                post.getId(), member.getId(), content, post.getCreatedAt()));
        return toCreateResponse(post, member, savedAttachments);
    }

    public PostDto.PostResponse getPost(Long projectId, Long postId, Long userId) {
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
        List<PostDto.PostResponse> posts = page.stream()
                .map(post -> toResponse(post, member, attachmentsByPostId.getOrDefault(post.getId(), List.of())))
                .toList();
        String nextCursor = hasNext && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1)) : null;
        PostDto.PostResponse notice = postRepository.findFirstByProjectMemberProjectIdAndIsNoticeTrue(projectId)
                .map(post -> toResponse(post, member, attachmentRepository.findAllByPostIdOrderByIdAsc(post.getId())))
                .orElse(null);
        return new PostDto.FeedResponse(notice, posts, nextCursor, hasNext);
    }

    public PostDto.NoticeListResponse getNotices(Long projectId, Long userId) {
        requireProject(projectId);
        ProjectMember member = requireActiveMember(projectId, userId);
        List<PostDto.PostResponse> notices = postRepository
                .findAllByProjectMemberProjectIdAndNoticedAtIsNotNullOrderByNoticedAtDescIdDesc(projectId)
                .stream()
                .map(post -> toResponse(post, member,
                        attachmentRepository.findAllByPostIdOrderByIdAsc(post.getId())))
                .toList();
        return new PostDto.NoticeListResponse(notices);
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
        if (request.title() != null) {
            post.updateTitle(requireContent(request.title(), 100));
        }
        List<PostAttachment> resultingAttachments;
        if (request.attachments() != null) {
            List<UploadedFile> resolvedFiles =
                    validateAttachments(userId, postId, request.attachments());
            List<Long> previousFileIds = attachmentRepository.findFileIdsByPostId(postId);
            Set<Long> keptFileIds = resolvedFiles.stream()
                    .filter(Objects::nonNull)
                    .map(UploadedFile::getId)
                    .collect(Collectors.toSet());
            List<Long> removedFileIds = previousFileIds.stream()
                    .filter(fileId -> !keptFileIds.contains(fileId)).toList();
            // UNIQUE(file_id) 때문에 delete 가 insert 보다 먼저 flush 돼야 한다.
            // 이 flush 를 지우면 같은 file_id 재삽입이 제약 위반으로 터진다.
            attachmentRepository.deleteAllByPostId(postId);
            attachmentRepository.flush();
            resultingAttachments = saveAttachments(post, request.attachments(), resolvedFiles);
            uploadedFileService.release(removedFileIds);
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
        projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ApiException(ProjectApiErrorCode.PROJECT_NOT_FOUND));
        Post post = requirePost(projectId, postId);
        if (!post.getProjectMember().getId().equals(member.getId()) && member.getRole() != ProjectRole.OWNER) {
            throw new ApiException(PostErrorCode.POST_DELETE_PERMISSION_DENIED);
        }
        List<Long> fileIds = attachmentRepository.findFileIdsByPostId(postId);
        boolean deletedCurrentNotice = post.isNotice();
        postRepository.delete(post);
        postRepository.flush();
        if (deletedCurrentNotice) {
            postRepository.findFirstByProjectMemberProjectIdAndNoticedAtIsNotNullOrderByNoticedAtDescIdDesc(projectId)
                    .ifPresent(Post::restoreNotice);
        }
        uploadedFileService.release(fileIds);
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
        boolean newlyPublished = Boolean.TRUE.equals(request.isNotice()) && !post.isNotice();
        if (Boolean.TRUE.equals(request.isNotice())) {
            clearNotices(projectId, postId);
        }
        post.changeNotice(Boolean.TRUE.equals(request.isNotice()));
        postRepository.saveAndFlush(post);
        if (newlyPublished) {
            eventPublisher.publishEvent(new NoticePublishedEvent(projectId, postId));
        }
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
        eventPublisher.publishEvent(new CommentCreatedEvent(
                comment.getId(), post.getId(), member.getId(), content, comment.getCreatedAt()));
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

    private PostDto.PostResponse toResponse(Post post, ProjectMember viewer, List<PostAttachment> attachments) {
        List<PostDto.AttachmentResponse> attachmentResponses = attachments.stream().map(this::toAttachmentResponse).toList();
        return new PostDto.PostResponse(
                post.getId(), post.getProjectMember().getProject().getId(), post.getProjectMember().getId(),
                post.getProjectMember().getDisplayNickname(),
                post.getProjectMember().getUser().getProfilePreset(),
                post.getTitle(), post.getContent(), post.isNotice(),
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
                post.getProjectMember().getDisplayNickname(),
                post.getProjectMember().getUser().getProfilePreset(),
                post.getTitle(), post.getContent(), post.isNotice(), postLikeRepository.countByPostId(post.getId()),
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
                post.getProjectMember().getDisplayNickname(),
                post.getProjectMember().getUser().getProfilePreset(),
                post.getTitle(), post.getContent(), post.isNotice(), postLikeRepository.countByPostId(post.getId()),
                commentRepository.countByPostId(post.getId()),
                postLikeRepository.existsByPostIdAndProjectMemberId(post.getId(), viewer.getId()),
                attachments.stream().map(this::toAttachmentResponse).toList(),
                toInstant(post.getUpdatedAt()));
    }

    private PostDto.AttachmentResponse toAttachmentResponse(PostAttachment attachment) {
        UploadedFile file = attachment.getUploadedFile();
        String name = file != null ? file.getOriginalFilename() : attachment.getLinkName();
        Long fileId = file != null ? file.getId() : null;
        // FILE 은 presigned 를 담지 않는다. 조회 시점에 발급하면 사용자가 클릭할 때쯤
        // 만료돼 다운로드가 실패한다. 발급 API 주소만 알려주고 클릭 시점에 받아가게 한다.
        // 판정은 uploadedFile 유무가 아니라 attachmentType 으로 한다 — 다운로드 서비스가
        // "FILE 인데 파일이 없음"을 404 로 방어하므로 기준을 맞춘다(task 쪽도 동일).
        String downloadUrlApi = attachment.getAttachmentType() == AttachmentType.FILE
                ? downloadUrlFactory.forPost(
                        attachment.getPost().getProjectMember().getProject().getId(),
                        attachment.getId())
                : null;
        return new PostDto.AttachmentResponse(attachment.getId(), attachment.getAttachmentType(),
                fileId, name, attachment.getFileSize(), attachment.getLinkUrl(), downloadUrlApi);
    }

    private CommentDto.Response toCommentResponse(Comment comment) {
        return new CommentDto.Response(
                comment.getId(), comment.getPost().getId(),
                comment.getPost().getProjectMember().getProject().getId(), comment.getProjectMember().getId(),
                comment.getProjectMember().getDisplayNickname(),
                comment.getProjectMember().getUser().getProfilePreset(),
                comment.getContent(), toInstant(comment.getCreatedAt()));
    }

    private List<PostAttachment> saveAttachments(Post post,
                                                 List<PostDto.AttachmentRequest> requests,
                                                 List<UploadedFile> resolved) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<PostAttachment> attachments = IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    PostDto.AttachmentRequest request = requests.get(index);
                    UploadedFile file = resolved.get(index);
                    return PostAttachment.builder()
                            .post(post)
                            .attachmentType(request.attachmentType())
                            .uploadedFile(file)
                            .linkUrl(file == null ? request.linkUrl() : null)
                            .linkName(file == null ? request.fileName() : null)
                            .fileSize(request.fileSize())
                            .build();
                })
                .toList();
        return attachmentRepository.saveAllAndFlush(attachments);
    }

    /**
     * fileKey 는 신규(PENDING 확정), fileId 는 기존 유지(이 리소스 소유 확인).
     * postId 가 null 이면 생성 경로라 fileId 를 허용하지 않는다.
     * <p>
     * 반환 리스트는 요청과 인덱스가 대응한다. LINK 자리는 null 이다.
     */
    private List<UploadedFile> validateAttachments(
            Long userId, Long postId, List<PostDto.AttachmentRequest> requests) {
        attachmentPolicy.validateCount(requests.size(), PostErrorCode.VALIDATION_ERROR);
        Set<Long> ownedFileIds = postId == null
                ? Set.of()
                : Set.copyOf(attachmentRepository.findFileIdsByPostId(postId));
        List<UploadedFile> resolved = new ArrayList<>();
        for (PostDto.AttachmentRequest request : requests) {
            if (request == null || request.attachmentType() == null) {
                throw new ApiException(PostErrorCode.VALIDATION_ERROR);
            }
            if (request.attachmentType() != AttachmentType.FILE) {
                attachmentPolicy.validateLink(request.linkUrl(), PostErrorCode.INVALID_LINK_URL);
                resolved.add(null);
                continue;
            }
            if ((request.fileKey() == null) == (request.fileId() == null)) {
                throw new ApiException(PostErrorCode.VALIDATION_ERROR);
            }
            resolved.add(request.fileId() != null
                    ? uploadedFileService.requireOwnedByResource(
                            request.fileId(), ownedFileIds, PostErrorCode.VALIDATION_ERROR)
                    : attachmentPolicy.confirmFileAttachment(AttachmentUsage.POST, userId,
                            request.fileName(), request.fileSize(), request.fileKey(),
                            PostErrorCode.VALIDATION_ERROR));
        }
        return resolved;
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
