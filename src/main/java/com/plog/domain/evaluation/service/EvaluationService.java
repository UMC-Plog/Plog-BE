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

        // TODO: ProjectStatus에 'EVALUATING(평가 대기)' 상태가 추가

        ProjectMember currentMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));


        List<ProjectMember> allMembers = projectMemberRepository.findAllByProjectId(projectId);

        List<TargetMemberDto> targets = allMembers.stream()
                .filter(member -> !member.getId().equals(currentMember.getId())) // 본인 제외
                .map(member -> {

                    boolean isEvaluated = peerEvaluationRepository.existsByEvaluatorAndEvaluatee(currentMember, member);

                    return TargetMemberDto.builder()
                            .projectMemberId(member.getId())
                            .nickname(member.getAnNickname() != null ? member.getAnNickname() : member.getUser().getNickname()) // 익명 닉네임이 있다면 우선 사용
                            .isEvaluated(isEvaluated)
                            .build();
                })
                .collect(Collectors.toList());

        return new EvaluationTargetResponse(targets);
    }
}