package com.plog.domain.chat.service;

import com.plog.domain.chat.dto.response.ChatChannelParticipantResponse;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatChannelParticipantService {

    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public Map<Long, List<ChatChannelParticipantResponse>> getParticipantsByProjectIds(
            List<Long> projectIds
    ) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        return projectMemberRepository.findActiveMembers(projectIds, MemberStatus.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(
                        member -> member.getProject().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toResponse, Collectors.toList())
                ));
    }

    private ChatChannelParticipantResponse toResponse(ProjectMember member) {
        return new ChatChannelParticipantResponse(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl()
        );
    }
}
