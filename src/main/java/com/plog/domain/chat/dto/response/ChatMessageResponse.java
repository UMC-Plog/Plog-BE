package com.plog.domain.chat.dto.response;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.global.util.TimeUtil;

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
    
    // 메시지 목록/상세와 실시간 브로드캐스트가 같은 변환을 쓰도록 한 곳에 모으기
    public static ChatMessageResponse of(
            ChatMessage chatMessage,
            List<ChatMessageAttachmentResponse> attachments
    ) {
        ProjectMember sender = chatMessage.getProjectMember();
        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getChatRoom().getId(),
                chatMessage.getMessageSequence(),
                sender.getId(),
                sender.getDisplayNickname(),
                sender.getUser().getProfilePreset(),
                chatMessage.getMessage(),
                attachments,
                TimeUtil.toInstant(chatMessage.getCreatedAt())
        );
    }

    /**
     * fileUrl 은 만료 없는 프록시 절대 URL 이다(presigned 아님).
     * <p>
     * 세 필드의 조합이 프론트의 동작을 결정한다.
     * <ul>
     *   <li>thumbnailUrl 있음 → 썸네일 표시</li>
     *   <li>thumbnailUrl null + thumbnailPending true → <b>원본을 요청하지 않고</b>
     *       스켈레톤. /topic/chat-rooms/{roomId}/attachments 로 오는 push 를 기다린다</li>
     *   <li>thumbnailUrl null + thumbnailPending false → 원본(fileUrl) 표시.
     *       비이미지이거나 생성에 실패한 경우다</li>
     * </ul>
     * thumbnailPending 이 없으면 프론트가 뒤의 두 경우를 구분할 수 없어, 브로드캐스트
     * 시점에 방을 보고 있는 전원이 원본 풀사이즈를 받아 간다. 썸네일을 만든 이유가 사라진다.
     * <p>
     * 두 URL 을 나눠 두는 이유는 같은 URL 이 "없으면 원본, 생기면 썸네일"로 동작하면
     * 그 자원이 불변이 아니게 되어 Cache-Control: immutable 과 충돌하기 때문이다.
     */
    public record ChatMessageAttachmentResponse(
            Long chatAttachmentId,
            String fileName,
            Long fileSize,
            String fileUrl,
            String thumbnailUrl,
            boolean thumbnailPending
    ) {
    }
}