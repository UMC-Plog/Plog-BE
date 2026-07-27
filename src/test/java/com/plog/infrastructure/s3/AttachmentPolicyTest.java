package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.post.exception.PostErrorCode;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentPolicyTest {

    private static final String KEY = "tasks/users/3/a/spec.docx";

    @Mock
    private UploadedFileService uploadedFileService;

    private AttachmentPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AttachmentPolicy(uploadedFileService);
    }

    @Test
    void allowsUpToTenAttachments() {
        assertThatCode(() -> policy.validateCount(10, TaskErrorCode.INVALID_ATTACHMENT))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMoreThanTenAttachments() {
        assertThatThrownBy(() -> policy.validateCount(11, TaskErrorCode.INVALID_ATTACHMENT))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(TaskErrorCode.INVALID_ATTACHMENT));
    }

    @Test
    void delegatesFileAttachmentsToTheRegistry() {
        policy.confirmFileAttachment(AttachmentUsage.TASK, 3L, "spec.docx", 2048L, KEY,
                TaskErrorCode.INVALID_ATTACHMENT);

        verify(uploadedFileService).confirmNew(
                AttachmentUsage.TASK, 3L, KEY, "spec.docx", 2048L,
                TaskErrorCode.INVALID_ATTACHMENT);
    }

    @Test
    void rejectsAFileAttachmentMissingItsKey() {
        assertThatThrownBy(() -> policy.confirmFileAttachment(
                AttachmentUsage.TASK, 3L, "spec.docx", 2048L, null,
                TaskErrorCode.INVALID_ATTACHMENT))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(uploadedFileService);
    }

    @Test
    void 빈_문자열도_거부한다() {
        // 도메인마다 따로 검사하면 한쪽만 빠뜨린다. 실제로 chat 에만 있었고 task 에는 없었다.
        assertThatThrownBy(() -> policy.confirmFileAttachment(
                AttachmentUsage.TASK, 3L, "spec.docx", 2048L, "   ",
                TaskErrorCode.INVALID_ATTACHMENT))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.confirmFileAttachment(
                AttachmentUsage.TASK, 3L, "  ", 2048L, KEY,
                TaskErrorCode.INVALID_ATTACHMENT))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(uploadedFileService);
    }

    @Test
    void rejectsAFileAttachmentMissingItsSize() {
        assertThatThrownBy(() -> policy.confirmFileAttachment(
                AttachmentUsage.TASK, 3L, "spec.docx", null, KEY,
                TaskErrorCode.INVALID_ATTACHMENT))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(uploadedFileService);
    }

    @Test
    void preservesEachDomainsOwnErrorCode() {
        assertThatThrownBy(() -> policy.validateCount(11, PostErrorCode.VALIDATION_ERROR))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PostErrorCode.VALIDATION_ERROR));
    }

    @Test
    void acceptsAnHttpsLink() {
        assertThatCode(() -> policy.validateLink("https://example.com/doc",
                TaskErrorCode.INVALID_LINK_URL)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainHttp() {
        assertThatThrownBy(() -> policy.validateLink("http://example.com/doc",
                TaskErrorCode.INVALID_LINK_URL)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsLocalhostAndPrivateHostsToBlockSsrf() {
        assertThatThrownBy(() -> policy.validateLink("https://localhost/doc",
                TaskErrorCode.INVALID_LINK_URL)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validateLink("https://192.168.0.1/doc",
                TaskErrorCode.INVALID_LINK_URL)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validateLink("https://169.254.169.254/latest/meta-data",
                TaskErrorCode.INVALID_LINK_URL)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsALinkWithoutAScheme() {
        assertThatThrownBy(() -> policy.validateLink("example.com/doc",
                TaskErrorCode.INVALID_LINK_URL)).isInstanceOf(ApiException.class);
    }
}
