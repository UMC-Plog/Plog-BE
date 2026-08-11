package com.plog.infrastructure.s3;

import com.plog.global.api.code.BaseErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드 객체의 수명 오케스트레이션. 도메인 서비스는 S3 를 직접 호출하지 않고
 * 이 클래스만 쓴다.
 * <p>
 * S3 태깅은 여기서 하지 않는다 — status 를 바꾸고 tagged_at 을 비우면
 * UploadedFileTagScheduler 가 반영한다. S3 호출이 트랜잭션 경계 밖으로 새지 않고,
 * 실패해도 다음 틱에 자동 복구된다.
 */
@Service
@RequiredArgsConstructor
public class UploadedFileService {

    private final UploadedFileRepository uploadedFileRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ThumbnailProperties thumbnailProperties;

    @Transactional
    public FileStorageDto.PresignedUploadResponse issue(
            Long userId, FileStorageDto.PresignedUploadRequest request) {
        String fileKey = fileStorageService.buildKey(request.usage(), userId, request.fileName());
        FileStorageDto.PresignedUploadResponse presigned =
                fileStorageService.createUploadUrl(userId, request, fileKey);
        UploadedFile saved = uploadedFileRepository.save(UploadedFile.issue(
                fileKey, userId, request.usage(), request.fileName(),
                request.contentType(), request.fileSize(), TimeUtil.now()));
        return new FileStorageDto.PresignedUploadResponse(
                presigned.uploadUrl(), saved.getId(), fileKey,
                presigned.signedHeaders(), presigned.expiresAt());
    }

    /** 신규 첨부: 소유자·용도·업로드 실체를 확인하고 PENDING → CONFIRMED 로 옮긴다. */
    @Transactional
    public UploadedFile confirmNew(AttachmentUsage usage, Long userId, String fileKey,
                                   String fileName, long fileSize, BaseErrorCode invalidCode) {
        if (fileKey == null || fileName == null) {
            throw new ApiException(invalidCode);
        }
        UploadedFile file = uploadedFileRepository.findByFileKey(fileKey)
                .orElseThrow(() -> new ApiException(invalidCode));
        if (!file.getOwnerId().equals(userId)) {
            throw new ApiException(FileStorageErrorCode.FILE_NOT_OWNED);
        }
        if (file.getPurpose() != usage) {
            throw new ApiException(invalidCode);
        }
        if (!fileName.equals(file.getOriginalFilename())
                || file.getSize() == null || file.getSize() != fileSize) {
            throw new ApiException(invalidCode);
        }
        if (!fileStorageService.headMatches(fileKey, fileSize, file.getContentType())) {
            throw new ApiException(invalidCode);
        }
        LocalDateTime now = TimeUtil.now();
        int updated = uploadedFileRepository.confirmIfPending(
                fileKey, now, UploadedFileStatus.CONFIRMED, UploadedFileStatus.PENDING);
        if (updated == 0) {
            throw new ApiException(FileStorageErrorCode.FILE_ALREADY_ATTACHED);
        }
        file.confirm(now);
        markThumbnailTargetIfNeeded(usage, file);
        return file;
    }

    /**
     * 썸네일 대상이면 상태만 찍고 이벤트를 던진다. <b>여기서 Lambda 를 부르지 않는다</b> —
     * 이 메서드는 트랜잭션 안이라 커밋 전에 Invoke 가 나가고, 롤백되면 고아 썸네일이 남는다.
     * <p>
     * 판정 조건 셋이 각각 다른 것을 막는다. enabled: 꺼진 환경에 큐가 쌓이는 것 /
     * CHAT: 이번 범위 밖 도메인 / isImageContentType: pdf·zip 이 Lambda 를 깨우는 것.
     * POST·TASK 로 넓힐 때 바뀌는 것은 가운데 조건 하나뿐이다.
     */
    private void markThumbnailTargetIfNeeded(AttachmentUsage usage, UploadedFile file) {
        if (!thumbnailProperties.enabled()
                || usage != AttachmentUsage.CHAT
                || !FileStorageService.isImageContentType(file.getContentType())) {
            return;
        }
        file.markThumbnailPending();
        eventPublisher.publishEvent(new ThumbnailRequestedEvent(file.getId()));
    }

    /**
     * 기존 첨부 유지: 이 리소스가 현재 참조 중인 file_id 인지 확인한다.
     * <p>
     * owner_id 만 보면 자기 파일을 다른 게시글에서 훔쳐올 수 있으므로 반드시
     * "이 리소스 소유인가"를 본다.
     */
    @Transactional(readOnly = true)
    public UploadedFile requireOwnedByResource(Long fileId, Set<Long> resourceFileIds,
                                               BaseErrorCode invalidCode) {
        if (fileId == null || !resourceFileIds.contains(fileId)) {
            throw new ApiException(invalidCode);
        }
        return uploadedFileRepository.findById(fileId)
                .orElseThrow(() -> new ApiException(invalidCode));
    }

    /** 참조 해제. 실제 S3 삭제는 Lifecycle 이 태그 기준으로 처리한다. */
    @Transactional
    public void release(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        LocalDateTime now = TimeUtil.now();
        List<UploadedFile> files = uploadedFileRepository.findAllById(fileIds);
        files.forEach(file -> file.release(now));
        uploadedFileRepository.saveAll(files);
    }
}
