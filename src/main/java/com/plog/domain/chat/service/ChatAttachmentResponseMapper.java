package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatMessageResponse;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.global.config.MediaProperties;
import com.plog.infrastructure.s3.UploadedFile;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 채팅 첨부 → 응답 DTO 변환. 목록 조회와 WebSocket 브로드캐스트가 <b>같은</b> 변환을
 * 쓰도록 한 곳에 모은다. 전에는 두 곳에 복붙되어 있어 한쪽만 고치면 "목록은 되는데
 * 방금 보낸 사진만 안 뜬다"가 나기 쉬웠다.
 */
@Component
public class ChatAttachmentResponseMapper {

    private static final String PATH_PREFIX = "/api/chat-attachments/";

    private final String baseUrl;

    public ChatAttachmentResponseMapper(MediaProperties mediaProperties) {
        String configured = mediaProperties.baseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    public List<ChatMessageResponse.ChatMessageAttachmentResponse> toResponses(
            List<ChatAttachment> attachments) {
        return attachments.stream().map(this::toResponse).toList();
    }

    public ChatMessageResponse.ChatMessageAttachmentResponse toResponse(ChatAttachment attachment) {
        UploadedFile file = attachment.getUploadedFile();
        return new ChatMessageResponse.ChatMessageAttachmentResponse(
                attachment.getId(),
                file.getOriginalFilename(),
                file.getSize(),
                baseUrl + PATH_PREFIX + attachment.getId(),
                // 썸네일 파이프라인은 아직 없다. 계약만 고정한다.
                null);
    }
}
