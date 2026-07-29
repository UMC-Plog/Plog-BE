package com.plog.domain.task.service;

import com.plog.domain.project.service.ProjectAccessService;
import com.plog.domain.task.entity.AttachmentType;
import com.plog.domain.task.entity.TaskAttachment;
import com.plog.domain.task.repository.TaskAttachmentRepository;
import com.plog.global.api.error.TaskErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.api.response.AttachmentDownloadResponse;
import com.plog.infrastructure.s3.FileStorageDto;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.UploadedFile;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무카드 첨부 다운로드 URL 을 클릭 시점에 발급한다.
 * <p>
 * 조회 응답에 미리 담아 보내면 사용자가 언제 클릭할지 알 수 없어 만료된 URL 을 쥐게 된다.
 * 발급과 사용 사이가 몇 초가 되도록 여기서 만든다. ReportPdfDownloadService 와 같은 방식이다.
 */
@Service
@RequiredArgsConstructor
public class TaskAttachmentDownloadService {

    private static final Duration DOWNLOAD_URL_DURATION = Duration.ofSeconds(300);

    private final TaskAttachmentRepository taskAttachmentRepository;
    private final ProjectAccessService projectAccessService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public AttachmentDownloadResponse createDownloadUrl(
            Long projectId, Long taskAttachmentId, Long userId) {

        // 멤버십을 가장 먼저 본다. 조회를 먼저 하면 비멤버가 404(없음/남의 것)와 403(이 방 것)의
        // 차이로 "이 첨부가 저 프로젝트 소속이다"를 알아낼 수 있다. 순서를 바꾸면 비멤버는
        // 무엇을 물어도 403 하나만 받아 아무것도 배우지 못한다. 덤으로 쿼리도 아낀다.
        projectAccessService.requireActiveMember(projectId, userId);

        TaskAttachment attachment = taskAttachmentRepository
                .findWithFileAndProjectById(taskAttachmentId)
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_ATTACHMENT_NOT_FOUND));

        // requireActiveMember 는 "이 사람이 projectId 의 멤버인가"만 본다. 첨부가 그 프로젝트
        // 것인지는 안 보므로 여기서 대조하지 않으면 남의 프로젝트 파일을 받을 수 있다.
        Long ownerProjectId = attachment.getTask().getProjectMember().getProject().getId();
        if (!ownerProjectId.equals(projectId)) {
            throw new ApiException(TaskErrorCode.TASK_ATTACHMENT_NOT_FOUND);
        }

        if (attachment.getAttachmentType() != AttachmentType.FILE) {
            throw new ApiException(TaskErrorCode.INVALID_ATTACHMENT);
        }
        UploadedFile file = attachment.getUploadedFile();
        if (file == null) {
            // FILE 인데 파일이 없으면 정합성이 깨진 상태다. NPE 대신 404 로 끊는다.
            throw new ApiException(TaskErrorCode.TASK_ATTACHMENT_NOT_FOUND);
        }

        FileStorageDto.PresignedDownloadResponse presigned = fileStorageService.createDownloadUrl(
                file.getFileKey(), file.getOriginalFilename(), DOWNLOAD_URL_DURATION);

        return new AttachmentDownloadResponse(
                attachment.getId(),
                file.getOriginalFilename(),
                presigned.downloadUrl(),
                presigned.expiresInSeconds());
    }
}
