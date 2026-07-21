package com.plog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.plog.domain.post.entity.Post;
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
import com.plog.infrastructure.s3.AttachmentPolicy;
import com.plog.infrastructure.s3.FileStorageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PostServicePolicyTest {
    @Mock private PostRepository postRepository;
    @Mock private PostAttachmentRepository attachmentRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AttachmentPolicy attachmentPolicy;
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
                fileStorageService,
                attachmentPolicy,
                eventPublisher
        );
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
    void missingProjectUsesDocumentedErrorCode() {
        when(projectRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.getPost(404L, 1L, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ProjectApiErrorCode.PROJECT_NOT_FOUND));
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
        when(postRepository.findByIdAndProjectMemberProjectId(2L, 1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.updatePost(
                1L, 2L, 7L, new com.plog.domain.post.dto.PostDto.UpdateRequest("updated", null)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.POST_UPDATE_PERMISSION_DENIED));
    }
}
