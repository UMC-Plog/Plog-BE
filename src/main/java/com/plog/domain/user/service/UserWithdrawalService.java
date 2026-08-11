package com.plog.domain.user.service;

import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.service.ProjectPurgeService;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.AuthErrorCode;
import com.plog.global.api.error.UserErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.WithdrawalProperties;
import com.plog.global.util.TimeUtil;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 계정은 즉시 소프트 삭제하고, 유예기간이 지나면 개인정보를 파기한다(purgeExpired).
 * 작성한 게시글·댓글·채팅은 지우지 않는다 — 다른 멤버가 보던 화면이 깨지기 때문이다.
 * 본인이 OWNER인 프로젝트는 남은 활성 멤버에게 넘기고, 내가 마지막 활성 멤버였던 프로젝트는 완전 삭제한다
 * (ProjectLeaveService.leave의 마지막 멤버 퇴장과 같은 조건·같은 처리).
 */
@Slf4j
@Service
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectPurgeService projectPurgeService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final EmailVerificationCodeService emailVerificationCodeService;
    private final WithdrawnUserAnonymizer withdrawnUserAnonymizer;
    private final WithdrawalProperties properties;

    public UserWithdrawalService(UserRepository userRepository,
                                 ProjectMemberRepository projectMemberRepository,
                                 ProjectPurgeService projectPurgeService,
                                 RefreshTokenRepository refreshTokenRepository,
                                 FcmTokenRepository fcmTokenRepository,
                                 EmailVerificationCodeService emailVerificationCodeService,
                                 WithdrawnUserAnonymizer withdrawnUserAnonymizer,
                                 WithdrawalProperties properties) {
        this.userRepository = userRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.projectPurgeService = projectPurgeService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.fcmTokenRepository = fcmTokenRepository;
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.withdrawnUserAnonymizer = withdrawnUserAnonymizer;
        this.properties = properties;
    }

    @Transactional
    public void withdraw(Long userId, boolean agreed) {
        if (!agreed) {
            throw new ApiException(UserErrorCode.WITHDRAWAL_NOT_AGREED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_TOKEN));
        if (user.isWithdrawn()) {
            throw new ApiException(AuthErrorCode.WITHDRAWN_ACCOUNT);
        }
        // 아래 토큰 삭제(@Modifying clearAutomatically)로 영속성 컨텍스트가 비워지기 전에 읽어둔다.
        String email = user.getEmail();

        // 멤버십 목록은 한 번만 조회해 두 단계가 같은 리스트를 공유한다(같은 트랜잭션이므로 동일 엔티티).
        List<ProjectMember> myMemberships = projectMemberRepository.findAllByUserId(userId);

        // 순서 의존: 소유권 이전은 "내가 ACTIVE OWNER"인 멤버십만 대상으로 한다.
        // 먼저 퇴장(status=EXIT)시키면 이전 대상이 사라져 주인 없는 프로젝트가 남는다.
        handOverOwnedProjects(myMemberships);
        // 순서 의존: 삭제 대상 판정도 퇴장 전에 끝내야 한다(아래 메서드 주석 참고).
        List<Project> emptyProjects = findProjectsLosingLastActiveMember(myMemberships);
        leaveAllProjects(myMemberships);
        deleteProjects(emptyProjects);

        // 아래 토큰 삭제는 @Modifying(clearAutomatically) 로 영속성 컨텍스트를 비우므로,
        // user 엔티티 변경(탈퇴 시각)은 그 전에 반드시 DB에 반영돼야 한다.
        // fcmTokenRepository의 flushAutomatically 속성이나 호출 순서에만 기대면, 둘 중 하나만 바뀌어도
        // 탈퇴 시각이 조용히 유실될 수 있으므로 다른 리포지토리의 설정과 무관하게 여기서 명시적으로 flush 한다.
        user.withdraw(TimeUtil.now());
        userRepository.flush();
        refreshTokenRepository.deleteAllByUserId(userId);
        fcmTokenRepository.deleteAllByUserId(userId);
        // 탈퇴 즉시 이 이메일의 인증 행을 전부 지운다(목적 무관).
        // email_verification 행은 가입/재설정 "성공" 시에만 소비되고 TTL 스위퍼도 없어서,
        // 재설정 코드만 받고 흐름을 버린 뒤 탈퇴하면 실제 이메일이 그 표에 무기한 남는다
        // (개인정보 파기 배치는 tb_user만 건드린다). 계정이 죽는 이 시점이 유일하게 확실한 제거 지점이다.
        // 미사용/인증완료 행이 함께 정리되므로 재가입·재설정 흐름의 재사용 위험도 사라진다.
        emailVerificationCodeService.deleteAllByEmail(email);
    }

    /**
     * 본인이 OWNER인 프로젝트의 소유권을 남은 활성 멤버 중 최초 합류자에게 넘긴다.
     * 넘길 후보가 없으면 아무것도 하지 않는다 — 그 프로젝트는
     * findProjectsLosingLastActiveMember가 "남은 활성 멤버 0"으로 잡아 삭제한다.
     * (삭제 판정을 한 곳에만 두어 같은 프로젝트를 두 번 purge하는 경로를 만들지 않는다.)
     */
    private void handOverOwnedProjects(List<ProjectMember> myMemberships) {
        for (ProjectMember mine : myMemberships) {
            if (mine.getRole() != ProjectRole.OWNER || mine.getStatus() != MemberStatus.ACTIVE) {
                continue;
            }
            ProjectMember successor = findSuccessor(mine);
            if (successor != null) {
                // transferOwnershipTo는 내 role을 MEMBER로 내리고 후임을 OWNER로 올린다(양쪽 변경).
                mine.transferOwnershipTo(successor);
            }
        }
    }

    /**
     * 후임자 = 나를 제외한 활성 멤버 중 최초 합류자.
     * createdAt이 같은 멤버가 있으면 id로 확정한다 — 행 순서에 좌우되지 않게.
     * findActiveMembers는 createdAt으로 정렬하지 않고 project.id, member.id 순으로만 정렬한다;
     * 여기서 공유하는 건 "id 오름차순을 동률 해소 기준으로 쓴다"는 관례뿐이다.
     */
    private ProjectMember findSuccessor(ProjectMember mine) {
        return projectMemberRepository
                .findAllByProjectId(mine.getProject().getId()).stream()
                .filter(candidate -> !candidate.getId().equals(mine.getId()))
                .filter(candidate -> candidate.getStatus() == MemberStatus.ACTIVE)
                .min(Comparator.comparing(ProjectMember::getCreatedAt)
                        .thenComparing(ProjectMember::getId))
                .orElse(null);
    }

    /**
     * 내가 나가면 활성 멤버가 0이 되는 프로젝트를 찾는다 — ProjectLeaveService.leave의 삭제 조건과 동일하다.
     * leave 엔드포인트에는 OWNER 가드가 없어 "활성 멤버는 있는데 OWNER는 없는" 상태가 실제로 만들어진다.
     * 그래서 판정을 역할(OWNER)로 하면 안 된다: OWNER가 leave로 먼저 나간 프로젝트에 남은 MEMBER가 탈퇴할 때
     * 아무도 없는 프로젝트가 살아남고, 프로젝트 도메인은 deleted_at을 보지 않으므로 초대 링크로 되살아난다.
     * <p>
     * 순서 의존: leave() 전에 센다. "내 몫까지 포함한 활성 멤버 수 <= 1" == "내가 나간 뒤 0".
     * leave() 후에 세려면 UPDATE를 먼저 flush해야 하고, 그러면 삭제 대상이 하나도 없는 흔한 경우에도
     * 매번 flush가 발생한다. 같은 프로젝트의 멤버십이 중복으로 들어와도 한 번만 담는다(중복 purge 방지).
     */
    private List<Project> findProjectsLosingLastActiveMember(List<ProjectMember> myMemberships) {
        List<Project> targets = new ArrayList<>();
        Set<Long> checked = new HashSet<>();
        for (ProjectMember mine : myMemberships) {
            // 이미 EXIT인 멤버십은 내 탈퇴로 활성 멤버 수가 줄지 않는다(그때 같은 검사를 이미 거쳤다).
            if (mine.getStatus() != MemberStatus.ACTIVE) {
                continue;
            }
            Project project = mine.getProject();
            if (!checked.add(project.getId())) {
                continue;
            }
            long activeCount = projectMemberRepository
                    .countByProjectIdAndStatus(project.getId(), MemberStatus.ACTIVE);
            if (activeCount <= 1) {
                targets.add(project);
            }
        }
        return targets;
    }

    private void leaveAllProjects(List<ProjectMember> myMemberships) {
        myMemberships.stream()
                .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                .forEach(ProjectMember::leave);
    }

    /**
     * 남은 활성 멤버가 없는 프로젝트를 하위 데이터까지 완전 삭제한다.
     * ProjectLeaveService.leave의 "마지막 멤버 퇴장" 처리와 같은 경로다 — 한 상황에 두 의미를 두지 않기 위해.
     * 소프트 삭제는 프로젝트 도메인이 deleted_at을 걸러보지 않아(초대 토큰 조회 포함) 초대 링크로 되살아날 수 있으므로 쓰지 않는다.
     */
    private void deleteProjects(List<Project> emptyProjects) {
        if (emptyProjects.isEmpty()) {
            return;
        }
        // 벌크 삭제 전에 방금 바꾼 멤버 상태를 반영한다 — purge가 지운 행에 UPDATE가 뒤따르면 실패한다.
        projectMemberRepository.flush();
        for (Project project : emptyProjects) {
            // purge가 하위 데이터와 프로젝트 행까지 벌크로 삭제한다.
            // em.remove(project)를 쓰면 벌크 delete가 남긴 managed ProjectMember가
            // REMOVED 상태 project를 참조해 flush 시 TransientObjectException이 난다.
            projectPurgeService.purge(project.getId());
        }
    }

    /**
     * 유예기간이 지난 탈퇴 계정의 개인정보를 파기한다. 스케줄러가 호출한다. 반환값은 "성공한 건수".
     * <p>
     * 의도적으로 @Transactional 을 걸지 않는다. 배치 전체를 한 트랜잭션으로 묶으면 한 행의 실패가
     * 전부를 롤백시키고, 다음 밤에도 같은 행에서 같은 실패를 반복해 어떤 계정도 영구히 파기되지 않는다.
     * 행 단위 트랜잭션은 WithdrawnUserAnonymizer(별 빈, REQUIRES_NEW)가 각자 시작한다 —
     * 자기 클래스 메서드 호출은 프록시를 거치지 않아 격리가 걸리지 않기 때문에 빈을 분리했다.
     */
    public int purgeExpired() {
        LocalDateTime now = TimeUtil.now();
        LocalDateTime threshold = now.minus(properties.retention());
        List<User> targets = userRepository.findAllByDeletedAtBeforeAndAnonymizedAtIsNull(threshold);
        int purged = 0;
        for (User target : targets) {
            try {
                withdrawnUserAnonymizer.anonymize(target, now);
                purged++;
            } catch (RuntimeException e) {
                // 실패해도 배치는 계속 진행한다. 다만 조용히 넘기면 개인정보가 남은 사실을 아무도 모르므로,
                // 어느 계정인지(userId) 특정할 수 있게 ERROR로 남긴다 — 수동 조치의 유일한 단서다.
                log.error("탈퇴 계정 개인정보 파기 실패: userId={}", target.getId(), e);
            }
        }
        return purged;
    }
}
