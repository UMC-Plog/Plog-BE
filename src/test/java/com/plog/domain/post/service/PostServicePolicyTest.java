package com.plog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.post.dto.PostDto;
import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.Comment;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.event.CommentCreatedEvent;
import com.plog.domain.post.event.PostCreatedEvent;
import com.plog.domain.post.exception.PostErrorCode;
import com.plog.domain.post.repository.CommentRepository;
import com.plog.domain.post.repository.PostAttachmentRepository;
import com.plog.domain.post.repository.PostLikeRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.exception.ProjectApiErrorCode;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.FileStorageErrorCode;
import com.plog.global.common.AttachmentDownloadUrlFactory;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.plog.infrastructure.s3.UploadedFileService;

@ExtendWith(MockitoExtension.class)
class PostServicePolicyTest {
    @Mock private PostRepository postRepository;
    @Mock private PostAttachmentRepository attachmentRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ReportActivityLogRepository reportActivityLogRepository;
    @Mock private AttachmentDownloadUrlFactory downloadUrlFactory;
    @Mock private AttachmentPolicy attachmentPolicy;
    @Mock private UploadedFileService uploadedFileService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(
                postRepository,
                attachmentRepository,
                commentRepository,
                postLikeRepository,
                projectRepository,
                projectMemberRepository,
                reportActivityLogRepository,
                downloadUrlFactory,
                attachmentPolicy,
                uploadedFileService,
                eventPublisher
        );
    }

    @Test
    void 현재_공지_삭제는_프로젝트_잠금_안에서_이전_공지를_복원한다() {
        ProjectMember author = ProjectMember.builder()
                .id(3L).role(ProjectRole.OWNER).status(MemberStatus.ACTIVE).build();
        Post post = Post.builder()
                .id(2L).projectMember(author).title("공지").content("본문").isNotice(true).build();
        Project project = org.mockito.Mockito.mock(Project.class);
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(author));
        when(postRepository.findByIdAndProjectIdForUpdate(2L, 1L)).thenReturn(Optional.of(post));
        when(projectRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(project));
        when(attachmentRepository.findFileIdsByPostId(2L)).thenReturn(List.of());

        service.deletePost(1L, 2L, 7L);

        verify(projectRepository).findByIdForUpdate(1L);
        verify(reportActivityLogRepository).deletePostAndCommentActivities(2L);
        verify(postRepository)
                .findFirstByProjectMemberProjectIdAndNoticedAtIsNotNullOrderByNoticedAtDescIdDesc(1L);
    }

    @Test
    void rejectsNonPositiveFeedSizeWithValidationError() {
        ProjectMember member = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.getFeed(1L, 7L, null, 0))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.VALIDATION_ERROR));

        verifyNoInteractions(postRepository);
    }

    @Test
    void 수정_잠금_전에_게시글이_삭제되면_NOT_FOUND를_반환한다() {
        ProjectMember member = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(postRepository.findByIdAndProjectIdForUpdate(2L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePost(
                1L, 2L, 7L, new PostDto.UpdateRequest("수정 내용", null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));

        verifyNoInteractions(reportActivityLogRepository);
    }

    @Test
    void 이미_다른_게시글이_쓰는_fileKey는_거부한다() {
        ProjectMember member = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(attachmentPolicy.confirmFileAttachment(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ApiException(FileStorageErrorCode.FILE_ALREADY_ATTACHED));

        assertThatThrownBy(() -> service.createPost(1L, 7L, new PostDto.CreateRequest(
                "본문", false, List.of(new PostDto.AttachmentRequest(
                        AttachmentType.FILE, "a.pdf", 10L, "posts/users/7/id/a.pdf", null, null)))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.FILE_ALREADY_ATTACHED));
    }

    @Test
    void 다른_게시글의_fileId는_수정에서_거부한다() {
        ProjectMember author = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        Post post = Post.builder().projectMember(author).content("post").build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(author));
        when(postRepository.findByIdAndProjectIdForUpdate(2L, 1L)).thenReturn(Optional.of(post));
        // 이 게시글이 참조 중인 파일은 1L 뿐이다.
        when(attachmentRepository.findFileIdsByPostId(2L)).thenReturn(List.of(1L));
        when(uploadedFileService.requireOwnedByResource(eq(999L), any(), any()))
                .thenThrow(new ApiException(PostErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> service.updatePost(1L, 2L, 7L, new PostDto.UpdateRequest(
                null, List.of(new PostDto.AttachmentRequest(
                        AttachmentType.FILE, "a.pdf", 10L, null, 999L, null)))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.VALIDATION_ERROR));
    }

    @Test
    void fileKey와_fileId를_동시에_보내면_거부한다() {
        ProjectMember author = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        Post post = Post.builder().projectMember(author).content("post").build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(author));
        when(postRepository.findByIdAndProjectIdForUpdate(2L, 1L)).thenReturn(Optional.of(post));
        when(attachmentRepository.findFileIdsByPostId(2L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.updatePost(1L, 2L, 7L, new PostDto.UpdateRequest(
                null, List.of(new PostDto.AttachmentRequest(
                        AttachmentType.FILE, "a.pdf", 10L, "posts/users/7/id/a.pdf", 1L, null)))))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.VALIDATION_ERROR));
    }

    @Test
    void missingProjectUsesDocumentedErrorCode() {
        when(projectRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.getPost(404L, 1L, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectApiErrorCode.PROJECT_NOT_FOUND));
    }

    @Test
    void 게시글_생성_이벤트는_저장된_원본의_생성시각을_사용한다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 4, 10, 30);
        Project project = project(1L);
        ProjectMember member = member(3L, project);
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(postRepository.saveAndFlush(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            ReflectionTestUtils.setField(saved, "createdAt", createdAt);
            ReflectionTestUtils.setField(saved, "updatedAt", createdAt);
            return saved;
        });

        service.createPost(1L, 7L, new PostDto.CreateRequest("제목", "본문", false, List.of()));

        ArgumentCaptor<PostCreatedEvent> captor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PostCreatedEvent(11L, 3L, "본문", createdAt));
    }

    @Test
    void 댓글_생성_이벤트는_저장된_원본의_생성시각을_사용한다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 4, 11, 30);
        Project project = project(1L);
        ProjectMember member = member(3L, project);
        Post post = Post.builder().id(11L).projectMember(member).title("제목").content("본문").build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(member));
        when(postRepository.findByIdAndProjectMemberProjectId(11L, 1L)).thenReturn(Optional.of(post));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 31L);
            ReflectionTestUtils.setField(saved, "createdAt", createdAt);
            ReflectionTestUtils.setField(saved, "updatedAt", createdAt);
            return saved;
        });

        service.createComment(1L, 11L, 7L, new com.plog.domain.post.dto.CommentDto.CreateRequest(" 댓글 "));

        ArgumentCaptor<CommentCreatedEvent> captor = ArgumentCaptor.forClass(CommentCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CommentCreatedEvent(31L, 11L, 3L, "댓글", createdAt));
    }

    @Test
    void nonAuthorCannotUpdatePost() {
        ProjectMember viewer = ProjectMember.builder()
                .id(3L).role(ProjectRole.MEMBER).status(MemberStatus.ACTIVE).build();
        ProjectMember author = ProjectMember.builder().id(4L).build();
        Post post = Post.builder().projectMember(author).content("post").build();
        when(projectRepository.existsById(1L)).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserIdAndStatus(1L, 7L, MemberStatus.ACTIVE))
                .thenReturn(Optional.of(viewer));
        when(postRepository.findByIdAndProjectIdForUpdate(2L, 1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.updatePost(
                1L, 2L, 7L, new com.plog.domain.post.dto.PostDto.UpdateRequest("updated", null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_UPDATE_PERMISSION_DENIED));
    }

    private Project project(Long id) {
        return Project.builder()
                .id(id)
                .projectName("Plog")
                .inviteTokenHash("hash")
                .inviteTokenEncrypted("encrypted")
                .projectType(com.plog.domain.project.entity.ProjectType.DEVELOP)
                .status(com.plog.domain.project.entity.ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 8, 1))
                .endDay(LocalDate.of(2026, 8, 13))
                .build();
    }

    private ProjectMember member(Long id, Project project) {
        com.plog.domain.user.entity.User user = com.plog.domain.user.entity.User.createLocal(
                "user@example.com", "encoded", "구현모", "현모");
        return ProjectMember.builder()
                .id(id)
                .user(user)
                .project(project)
                .role(ProjectRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .build();
    }
}
