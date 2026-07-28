package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.MentionableMemberResponse;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMentionableMemberService {

    private final ChatRoomRepository chatRoomRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public List<MentionableMemberResponse> getMentionableMembers(Long roomId, Long userId, String keyword) {
        // findAccessibleRoom 자체가 "해당 프로젝트의 ACTIVE 멤버인지"까지 검증한다.
        // 요청자의 projectMemberId(본인 제외용)를 얻기 위해 별도로 한 번 더 조회한다.
        ChatRoom room = chatRoomRepository.findAccessibleRoom(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS));

        Long projectId = room.getProject().getId();
        Long requesterMemberId = projectMemberRepository
                .findByProjectIdAndUserIdAndStatus(projectId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS))
                .getId();

        // null을 그대로 쿼리에 넘기면 PostgreSQL이 파라미터 타입을 추론하지 못해 에러가 난다.
        // 빈 문자열로 정규화하면 LIKE '%%'가 되어 "keyword 없음 = 전체 조회"와 동일하게 동작한다.
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim();


        return projectMemberRepository
                .findMentionableMembers(projectId, MemberStatus.ACTIVE, requesterMemberId, normalizedKeyword)
                .stream()
                .map(MentionableMemberResponse::from)
                .toList();
    }
}