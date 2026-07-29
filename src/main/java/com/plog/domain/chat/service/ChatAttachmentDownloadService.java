package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatAttachmentDownload;
import com.plog.domain.chat.dto.response.ChatAttachmentMeta;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.UploadedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * 채팅 첨부 프록시. presigned 는 URL 이 한 번 새면 만료 전까지 누구나 받을 수 있었지만
 * 프록시는 <b>매 요청</b> 멤버십을 검사한다. 추방·탈퇴가 즉시 반영된다.
 * <p>
 * 조회·권한(resolve)과 스트림 열기(open)를 나눈다. 붙여 두면 조건부 요청에서 본문을
 * 쓰지 않는 경로에 커넥션이 새고, DB 트랜잭션이 S3 왕복 동안 커넥션을 잡는다.
 */
@Service
@RequiredArgsConstructor
public class ChatAttachmentDownloadService {

    private static final String THUMBNAIL_CONTENT_TYPE = "image/webp";

    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final FileStorageService fileStorageService;

    /** DB만 본다. S3를 열지 않으므로 트랜잭션이 외부 I/O에 묶이지 않는다. */
    @Transactional(readOnly = true)
    public ChatAttachmentMeta resolve(Long chatAttachmentId, Long userId) {
        UploadedFile file = findAccessible(chatAttachmentId, userId).getUploadedFile();
        // chat_attachments 의 uk_chat_attachment_file 이 file_id 를 UNIQUE 로 잡아 두어
        // 첨부:파일이 1:1이다. 그래서 uploaded_file.id 는 이 URL 의 안정적인 ETag 가 된다.
        return new ChatAttachmentMeta(
                file.getFileKey(),
                file.getContentType(),
                file.getOriginalFilename(),
                "\"" + file.getId() + "\"");
    }

    /**
     * 썸네일 메타. 원본과 <b>같은 권한 검사</b>를 거치되 키·타입·ETag 만 갈아 끼운다.
     * 그래서 컨트롤러와 open() 을 그대로 재사용할 수 있다.
     * <p>
     * ETag 에 -t 를 붙이는 이유는 두 URL 이 다른 자원이기 때문이다. 같은 값을 주면
     * 조건부 요청에서 브라우저가 원본 캐시를 썸네일로 착각한다.
     */
    @Transactional(readOnly = true)
    public ChatAttachmentMeta resolveThumbnail(Long chatAttachmentId, Long userId) {
        UploadedFile file = findAccessible(chatAttachmentId, userId).getUploadedFile();
        if (!file.isThumbnailReady() || file.getThumbnailKey() == null) {
            throw new ApiException(ChatErrorCode.CHAT_THUMBNAIL_NOT_FOUND);
        }
        return new ChatAttachmentMeta(
                file.getThumbnailKey(),
                THUMBNAIL_CONTENT_TYPE,
                file.getOriginalFilename(),
                "\"" + file.getId() + "-t\"");
    }

    private ChatAttachment findAccessible(Long chatAttachmentId, Long userId) {
        ChatAttachment attachment = chatAttachmentRepository
                .findWithFileAndRoomById(chatAttachmentId)
                .orElseThrow(() -> new ApiException(ChatErrorCode.CHAT_ATTACHMENT_NOT_FOUND));

        Long roomId = attachment.getChatMessage().getChatRoom().getId();
        chatRoomRepository.findAccessibleRoom(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));
        return attachment;
    }

    /**
     * S3 객체를 연다. 반드시 304 판정을 끝낸 뒤에 호출해야 한다.
     * <p>
     * DB 에는 있는데 S3 에 없는 경우도 404 로 합친다. 클라이언트가 할 수 있는 일이 같고,
     * 구분하면 "이 첨부는 존재는 한다"를 알려주는 셈이 된다.
     */
    public ChatAttachmentDownload open(ChatAttachmentMeta meta) {
        ResponseInputStream<GetObjectResponse> stream = fileStorageService
                .openStream(meta.fileKey())
                .orElseThrow(() -> new ApiException(ChatErrorCode.CHAT_ATTACHMENT_NOT_FOUND));

        // Content-Length 는 uploaded_file.size 가 아니라 S3 응답을 쓴다. size 는 nullable 이고,
        // 어차피 getObject 를 호출하므로 권위 있는 값이 손에 있다.
        return new ChatAttachmentDownload(stream.response().contentLength(), stream);
    }
}
