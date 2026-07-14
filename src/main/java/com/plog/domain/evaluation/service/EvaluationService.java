package com.plog.domain.evaluation.service;

import com.plog.domain.evaluation.dto.response.EvaluationTargetResponse;
import com.plog.domain.evaluation.dto.response.TargetMemberDto;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PeerEvaluationRepository peerEvaluationRepository;

    public EvaluationTargetResponse getEvaluationTargets(Long projectId, Long userId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        ProjectMember currentMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));

        List<ProjectMember> allMembers = projectMemberRepository.findAllByProjectId(projectId);

        Set<Long> evaluatedTargetIds = peerEvaluationRepository.findEvaluatedTargetIds(currentMember);

        List<TargetMemberDto> targets = allMembers.stream()
                .filter(member -> !member.getId().equals(currentMember.getId())) // 본인 제외
                .map(member -> {
                    
                    boolean isEvaluated = evaluatedTargetIds.contains(member.getId());

                    return TargetMemberDto.builder()
                            .projectMemberId(member.getId())
                            .nickname(member.getAnNickname() != null ? member.getAnNickname() : member.getUser().getNickname())
                            .isEvaluated(isEvaluated)
                            .build();
                })
                .collect(Collectors.toList());

        return new EvaluationTargetResponse(targets);
    }
}