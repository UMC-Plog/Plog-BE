package com.plog.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "대시보드 채팅방 목록/검색 항목")
public record ChatChannelResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "프로젝트 이름이자 채팅방 표시 이름", example = "Plog")
        String projectName,
        @Schema(description = "프로젝트에 자동 생성된 채팅방 ID", example = "20")
        Long roomId,
        @Schema(description = "마지막 메시지 내용. 메시지가 없으면 null", example = "회의록 올렸어요")
        String latestMessage,
        @Schema(description = "마지막 메시지 시각. 메시지가 없으면 null", example = "2026-07-24T13:30:00Z")
        Instant latestMessageAt,
        @Schema(description = "읽지 않은 메시지가 1개 이상이면 true", example = "true")
        boolean hasUnreadMessage,
        @Schema(description = "요청 사용자의 읽지 않은 메시지 수", example = "3")
        long unreadMessageCount,
        @Schema(description = "채팅방 이미지 조합에 사용할 참여자 프로필 목록")
        List<ChatChannelParticipantResponse> participants
) {
}
