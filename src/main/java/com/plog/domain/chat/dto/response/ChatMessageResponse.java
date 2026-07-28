package com.plog.domain.chat.dto.response;

import com.plog.domain.user.entity.ProfilePreset;

import java.time.Instant;
import java.util.List;

public record ChatMessageResponse(
        Long chatId,
        Long roomId,
        Long messageSequence,
        Long senderMemberId,
        String senderNickname,
        ProfilePreset profilePreset,
        String message,
        List<ChatMessageAttachmentResponse> attachments,
        Instant createdAt
) {
    /**
     * fileUrl 은 만료 없는 프록시 절대 URL 이다(presigned 아님).
     * <p>
     * thumbnailUrl 은 썸네일 파이프라인이 붙기 전까지 항상 null 이다. 프론트는
     * {@code thumbnailUrl ?? fileUrl} 로 짜두면 나중에 백엔드가 값을 채우기 시작할 때
     * 수정 없이 켜진다. 두 URL 을 나눠 두는 이유는 같은 URL 이 "없으면 원본, 생기면
     * 썸네일"로 동작하면 그 자원이 불변이 아니게 되어 Cache-Control: immutable 과
     * 충돌하기 때문이다.
     */
    public record ChatMessageAttachmentResponse(
            Long chatAttachmentId,
            String fileName,
            Long fileSize,
            String fileUrl,
            String thumbnailUrl
    ) {
    }
}