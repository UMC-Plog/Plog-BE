package com.plog.domain.task.service;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.global.common.AttachmentDownloadUrlFactory;
import org.springframework.stereotype.Component;

/**
 * FILE 첨부의 다운로드 URL 발급 엔드포인트 주소를 만든다. Query/Command 서비스 양쪽에서 공유.
 * <p>
 * 예전에는 여기서 presigned URL 을 직접 발급했다. 그러면 조회 시점에 만료 시계가 시작돼
 * 사용자가 나중에 클릭하면 죽은 URL 을 쥐게 되므로, 발급을 클릭 시점으로 옮겼다.
 * 여기 남는 것은 "그 발급 API 가 어디 있는지"뿐이다.
 */
@Component
public class TaskAttachmentUrlResolver {

    private final AttachmentDownloadUrlFactory downloadUrlFactory;

    public TaskAttachmentUrlResolver(AttachmentDownloadUrlFactory downloadUrlFactory) {
        this.downloadUrlFactory = downloadUrlFactory;
    }

    /** FILE 이면 발급 엔드포인트 절대 URL, LINK 면 null. */
    public String resolveDownloadUrlApi(Long projectId, TaskAttachment attachment) {
        return attachment.getAttachmentType() == AttachmentType.FILE
                ? downloadUrlFactory.forTask(projectId, attachment.getId())
                : null;
    }
}
