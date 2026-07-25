package com.plog.domain.task.service;

import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.infrastructure.s3.AttachmentUsage;
import com.plog.infrastructure.s3.FileStorageService;
import org.springframework.stereotype.Component;

// Task 첨부파일의 다운로드/원본 URL을 해석. Query/Command 서비스 양쪽에서 공유.
@Component
public class TaskAttachmentUrlResolver {

    private final FileStorageService fileStorageService;

    public TaskAttachmentUrlResolver(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public String resolve(TaskAttachment attachment) {
        return attachment.getAttachmentType() == AttachmentType.FILE
                ? fileStorageService.createDownloadUrl(
                AttachmentUsage.TASK, attachment.getFileUrl(), attachment.getFileName())
                : attachment.getFileUrl();
    }
}