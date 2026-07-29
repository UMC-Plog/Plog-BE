package com.plog.domain.chat.event;

import com.plog.domain.chat.dto.response.ChatThumbnailReadyResponse;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.service.ChatAttachmentResponseMapper;
import com.plog.infrastructure.s3.ThumbnailReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 썸네일이 준비되면 그 방에 URL 을 밀어 준다. 이게 없으면 프론트는 스켈레톤을 띄운 채
 * 목록을 다시 조회할 때까지 기다린다.
 * <p>
 * {@code @TransactionalEventListener} 가 아니라 {@code @EventListener} 다. 발행자
 * (ThumbnailScheduler)가 트랜잭션 밖에서 돌기 때문에 AFTER_COMMIT 을 걸면 이벤트가
 * 조용히 버려진다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatThumbnailReadyListener {

    private static final String CHAT_ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";
    private static final String ATTACHMENT_TOPIC_SUFFIX = "/attachments";

    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ChatAttachmentResponseMapper chatAttachmentResponseMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    @Transactional(readOnly = true)
    public void onThumbnailReady(ThumbnailReadyEvent event) {
        // 채팅이 아닌 도메인의 파일이면(향후 POST/TASK 확장) 보낼 방이 없다.
        chatAttachmentRepository.findWithFileAndRoomByUploadedFileId(event.uploadedFileId())
                .ifPresent(this::send);
    }

    private void send(ChatAttachment attachment) {
        String destination = CHAT_ROOM_TOPIC_PREFIX
                + attachment.getChatMessage().getChatRoom().getId()
                + ATTACHMENT_TOPIC_SUFFIX;
        try {
            messagingTemplate.convertAndSend(destination, new ChatThumbnailReadyResponse(
                    attachment.getId(),
                    chatAttachmentResponseMapper.thumbnailUrl(attachment)));
        } catch (MessagingException exception) {
            // 재시도하지 않는다. 상태는 이미 READY 라 다음 목록 조회에서 채워진다.
            log.warn("thumbnail_push_failed chatAttachmentId={}", attachment.getId(), exception);
        }
    }
}
