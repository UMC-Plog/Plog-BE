package com.plog.domain.task.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.infrastructure.s3.UploadedFile;
import org.junit.jupiter.api.Test;

/**
 * 업무카드 첨부 응답 DTO 4종의 필드 매핑 계약.
 * <p>
 * FILE 은 linkUrl 이 null 이고 downloadUrlApi 가 채워진다. LINK 는 그 반대다.
 * 이 계약이 무테스트였던 탓에 TaskUpdateResponse 한 종이 전환에서 누락돼
 * LINK 첨부의 링크가 응답에서 사라지는 회귀가 있었다. 4종을 한 파일에서 함께 검증해
 * 다음에 종이 늘거나 빠질 때 바로 드러나게 한다.
 */
class TaskAttachmentResponseMappingTest {

    private static final String DOWNLOAD_URL_API =
            "https://api.umc-plog.site/api/projects/1/tasks/attachments/3/download-url";
    private static final String LINK = "https://notion.so/spec";

    @Test
    void TaskCreateResponse_는_FILE과_LINK를_각각의_필드에_담는다() {
        TaskCreateResponse.AttachmentResponse file =
                TaskCreateResponse.AttachmentResponse.of(fileAttachment(), DOWNLOAD_URL_API);
        assertThat(file.linkUrl()).isNull();
        assertThat(file.downloadUrlApi()).isEqualTo(DOWNLOAD_URL_API);

        TaskCreateResponse.AttachmentResponse link =
                TaskCreateResponse.AttachmentResponse.of(linkAttachment(), null);
        assertThat(link.linkUrl()).isEqualTo(LINK);
        assertThat(link.downloadUrlApi()).isNull();
    }

    @Test
    void TaskUpdateResponse_는_FILE과_LINK를_각각의_필드에_담는다() {
        TaskUpdateResponse.AttachmentResponse file =
                TaskUpdateResponse.AttachmentResponse.of(fileAttachment(), DOWNLOAD_URL_API);
        assertThat(file.linkUrl()).isNull();
        assertThat(file.downloadUrlApi()).isEqualTo(DOWNLOAD_URL_API);

        TaskUpdateResponse.AttachmentResponse link =
                TaskUpdateResponse.AttachmentResponse.of(linkAttachment(), null);
        assertThat(link.linkUrl()).isEqualTo(LINK);
        assertThat(link.downloadUrlApi()).isNull();
    }

    @Test
    void TaskDetailResponse_는_FILE과_LINK를_각각의_필드에_담는다() {
        TaskDetailResponse.AttachmentResponse file =
                TaskDetailResponse.AttachmentResponse.of(fileAttachment(), DOWNLOAD_URL_API);
        assertThat(file.linkUrl()).isNull();
        assertThat(file.downloadUrlApi()).isEqualTo(DOWNLOAD_URL_API);
        assertThat(file.fileSize()).isEqualTo(2048L);

        TaskDetailResponse.AttachmentResponse link =
                TaskDetailResponse.AttachmentResponse.of(linkAttachment(), null);
        assertThat(link.linkUrl()).isEqualTo(LINK);
        assertThat(link.downloadUrlApi()).isNull();
        assertThat(link.fileSize()).isNull();
    }

    @Test
    void TaskAttachmentAddResponse_는_FILE과_LINK를_각각의_필드에_담는다() {
        TaskAttachmentAddResponse file =
                TaskAttachmentAddResponse.of(fileAttachment(), DOWNLOAD_URL_API);
        assertThat(file.linkUrl()).isNull();
        assertThat(file.downloadUrlApi()).isEqualTo(DOWNLOAD_URL_API);

        TaskAttachmentAddResponse link = TaskAttachmentAddResponse.of(linkAttachment(), null);
        assertThat(link.linkUrl()).isEqualTo(LINK);
        assertThat(link.downloadUrlApi()).isNull();
    }

    private TaskAttachment fileAttachment() {
        UploadedFile file = mock(UploadedFile.class);
        given(file.getId()).willReturn(11L);

        TaskAttachment attachment = mock(TaskAttachment.class);
        given(attachment.getId()).willReturn(3L);
        given(attachment.getAttachmentType()).willReturn(AttachmentType.FILE);
        given(attachment.getUploadedFile()).willReturn(file);
        given(attachment.displayName()).willReturn("요구사항_v2.docx");
        given(attachment.getFileSize()).willReturn(2048L);
        given(attachment.getLinkUrl()).willReturn(null);
        return attachment;
    }

    private TaskAttachment linkAttachment() {
        TaskAttachment attachment = mock(TaskAttachment.class);
        given(attachment.getId()).willReturn(4L);
        given(attachment.getAttachmentType()).willReturn(AttachmentType.LINK);
        given(attachment.getUploadedFile()).willReturn(null);
        given(attachment.displayName()).willReturn("설계 노션");
        given(attachment.getLinkUrl()).willReturn(LINK);
        return attachment;
    }
}
