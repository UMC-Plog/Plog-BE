package com.plog.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.post.entity.AttachmentType;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.entity.PostAttachment;
import com.plog.domain.post.exception.PostErrorCode;
import com.plog.domain.post.repository.PostAttachmentRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.service.ProjectAccessService;
import com.plog.global.api.error.ProjectErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.AttachmentDownloadResponse;
import com.plog.infrastructure.s3.FileStorageDto;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.UploadedFile;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PostAttachmentDownloadServiceTest {

    private final PostAttachmentRepository postAttachmentRepository =
            mock(PostAttachmentRepository.class);
    private final ProjectAccessService projectAccessService = mock(ProjectAccessService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final PostAttachmentDownloadService service = new PostAttachmentDownloadService(
            postAttachmentRepository, projectAccessService, fileStorageService);

    @Test
    void 첨부가_없으면_404() {
        given(postAttachmentRepository.findWithFileAndProjectById(3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.POST_ATTACHMENT_NOT_FOUND);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void 경로의_projectId와_첨부의_프로젝트가_다르면_404() {
        // 공격자가 자기 프로젝트 id 로 남의 첨부를 요청하는 경우. 403 으로 주면
        // "그 id 의 첨부가 다른 프로젝트에 존재한다"가 새므로 404 로 합친다.
        PostAttachment attachment = fileAttachment(99L);
        given(postAttachmentRepository.findWithFileAndProjectById(3L))
                .willReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.POST_ATTACHMENT_NOT_FOUND);
        // 멤버십을 먼저 통과한 뒤 대조에서 걸린다. presign 은 절대 일어나지 않는다.
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void 프로젝트_멤버가_아니면_403() {
        PostAttachment attachment = fileAttachment(1L);
        given(postAttachmentRepository.findWithFileAndProjectById(3L))
                .willReturn(Optional.of(attachment));
        given(projectAccessService.requireActiveMember(anyLong(), anyLong()))
                .willThrow(new ApiException(ProjectErrorCode.PROJECT_MEMBER_REQUIRED));

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.PROJECT_MEMBER_REQUIRED);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void LINK_첨부에_발급을_요청하면_400() {
        PostAttachment attachment = linkAttachment(1L);
        ProjectMember member = mock(ProjectMember.class);
        given(postAttachmentRepository.findWithFileAndProjectById(3L))
                .willReturn(Optional.of(attachment));
        given(projectAccessService.requireActiveMember(anyLong(), anyLong())).willReturn(member);

        assertThatThrownBy(() -> service.createDownloadUrl(1L, 3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", PostErrorCode.INVALID_ATTACHMENT_TYPE);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void 정상이면_presigned_와_파일명_만료를_담아_돌려준다() {
        PostAttachment attachment = fileAttachment(1L);
        ProjectMember member = mock(ProjectMember.class);
        given(postAttachmentRepository.findWithFileAndProjectById(3L))
                .willReturn(Optional.of(attachment));
        given(projectAccessService.requireActiveMember(anyLong(), anyLong())).willReturn(member);
        given(fileStorageService.createDownloadUrl(anyString(), anyString(), any(Duration.class)))
                .willReturn(new FileStorageDto.PresignedDownloadResponse(
                        "https://storage.test/signed", 300L));

        AttachmentDownloadResponse response = service.createDownloadUrl(1L, 3L, 7L);

        assertThat(response.attachmentId()).isEqualTo(3L);
        assertThat(response.fileName()).isEqualTo("요구사항_v2.docx");
        assertThat(response.downloadUrl()).isEqualTo("https://storage.test/signed");
        assertThat(response.expiresInSeconds()).isEqualTo(300L);
    }

    private PostAttachment fileAttachment(Long projectId) {
        UploadedFile file = mock(UploadedFile.class);
        given(file.getFileKey()).willReturn("posts/users/7/uuid/요구사항_v2.docx");
        given(file.getOriginalFilename()).willReturn("요구사항_v2.docx");

        PostAttachment attachment = attachmentOf(projectId);
        given(attachment.getAttachmentType()).willReturn(AttachmentType.FILE);
        given(attachment.getUploadedFile()).willReturn(file);
        return attachment;
    }

    private PostAttachment linkAttachment(Long projectId) {
        PostAttachment attachment = attachmentOf(projectId);
        given(attachment.getAttachmentType()).willReturn(AttachmentType.LINK);
        return attachment;
    }

    private PostAttachment attachmentOf(Long projectId) {
        Project project = mock(Project.class);
        given(project.getId()).willReturn(projectId);

        ProjectMember author = mock(ProjectMember.class);
        given(author.getProject()).willReturn(project);

        Post post = mock(Post.class);
        given(post.getProjectMember()).willReturn(author);

        PostAttachment attachment = mock(PostAttachment.class);
        given(attachment.getId()).willReturn(3L);
        given(attachment.getPost()).willReturn(post);
        return attachment;
    }
}
