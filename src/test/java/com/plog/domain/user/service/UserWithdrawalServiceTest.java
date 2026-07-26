package com.plog.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.plog.domain.notification.repository.FcmTokenRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.domain.project.entity.Project;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.entity.ProjectRole;
import com.plog.domain.project.entity.ProjectStatus;
import com.plog.domain.project.entity.ProjectType;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.project.service.ProjectPurgeService;
import com.plog.domain.user.entity.ProfilePreset;
import com.plog.domain.user.entity.ProviderType;
import com.plog.domain.user.entity.User;
import com.plog.domain.user.repository.RefreshTokenRepository;
import com.plog.domain.user.repository.UserRepository;
import com.plog.global.api.error.UserErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.global.config.WithdrawalProperties;
import com.plog.global.util.TimeUtil;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UserWithdrawalServiceTest {

    /** 합류 시각 기준점. createdAt은 JPA Auditing이 채우는 값이라 테스트에서 직접 주입한다. */
    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 7, 1, 9, 0);

    /** 파기 유예 기간. setUp의 WithdrawalProperties와 threshold 검증에서 같은 값을 참조한다. */
    private static final Duration RETENTION = Duration.ofDays(7);

    /** 익명화된 이메일/닉네임 형식(%d = user id, 뒤에 16진수 12자 임의값). */
    private static final String ANONYMIZED_EMAIL_PATTERN = "withdrawn-%d-[0-9a-f]{12}@deleted\\.plog";
    private static final String ANONYMIZED_NICKNAME_PATTERN = "탈퇴한사용자-%d-[0-9a-f]{12}";

    private UserRepository userRepository;
    private ProjectMemberRepository projectMemberRepository;
    private ProjectRepository projectRepository;
    private ProjectPurgeService projectPurgeService;
    private RefreshTokenRepository refreshTokenRepository;
    private FcmTokenRepository fcmTokenRepository;
    private EmailVerificationCodeService emailVerificationCodeService;
    private PasswordEncoder passwordEncoder;
    private UserWithdrawalService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectPurgeService = mock(ProjectPurgeService.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        fcmTokenRepository = mock(FcmTokenRepository.class);
        emailVerificationCodeService = mock(EmailVerificationCodeService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        // 행 단위 파기는 별 빈(WithdrawnUserAnonymizer)이 담당한다. 익명화 값 생성까지 함께 검증해야 하므로
        // 목이 아니라 실제 구현을 끼운다 — 리포지토리·인코더는 그대로 목이라 DB는 필요 없다.
        service = new UserWithdrawalService(userRepository, projectMemberRepository,
                projectRepository, projectPurgeService, refreshTokenRepository, fcmTokenRepository,
                emailVerificationCodeService, new WithdrawnUserAnonymizer(userRepository, passwordEncoder),
                new WithdrawalProperties(RETENTION));

        user = User.createLocal("a@plog.test", "encoded", "홍길동", "바나나", ProfilePreset.OTTER);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of());
    }

    @Test
    @DisplayName("동의하지 않으면 탈퇴할 수 없다")
    void requiresAgreement() {
        assertThatThrownBy(() -> service.withdraw(1L, false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(UserErrorCode.WITHDRAWAL_NOT_AGREED);

        assertThat(user.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("탈퇴하면 소프트 삭제되고 세션과 푸시 토큰이 폐기된다")
    void withdrawSoftDeletesAndClearsTokens() {
        service.withdraw(1L, true);

        assertThat(user.isWithdrawn()).isTrue();
        verify(refreshTokenRepository).deleteAllByUserId(1L);
        verify(fcmTokenRepository).deleteAllByUserId(1L);
        // fcmTokenRepository.deleteAllByUserId는 clearAutomatically=true라 영속성 컨텍스트를 비운다.
        // user.withdraw()로 바뀐 탈퇴 시각이 유실되지 않으려면 그 전에 flush가 끝나 있어야 한다.
        InOrder inOrder = inOrder(userRepository, fcmTokenRepository);
        inOrder.verify(userRepository).flush();
        inOrder.verify(fcmTokenRepository).deleteAllByUserId(1L);
    }

    @Test
    @DisplayName("이미 탈퇴한 계정은 다시 탈퇴할 수 없다")
    void alreadyWithdrawnRejected() {
        service.withdraw(1L, true);

        assertThatThrownBy(() -> service.withdraw(1L, true))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("탈퇴 시 멤버십을 한 번만 조회한다")
    void fetchesMembershipsOnce() {
        service.withdraw(1L, true);

        verify(projectMemberRepository, times(1)).findAllByUserId(1L);
    }

    @Test
    @DisplayName("OWNER가 탈퇴하면 남은 활성 멤버 중 최초 합류자가 새 OWNER가 된다")
    void handsOwnershipToEarliestActiveMember() {
        Project project = project(10L);
        ProjectMember mine = member(100L, project, ProjectRole.OWNER, MemberStatus.ACTIVE, JOINED_AT);
        ProjectMember later = member(201L, project, ProjectRole.MEMBER, MemberStatus.ACTIVE, JOINED_AT.plusDays(3));
        ProjectMember earliest = member(202L, project, ProjectRole.MEMBER, MemberStatus.ACTIVE, JOINED_AT.plusDays(1));
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of(mine));
        // 정렬 없는 파생 쿼리를 흉내내어 합류 순서와 무관한 행 순서로 반환한다.
        given(projectMemberRepository.findAllByProjectId(10L)).willReturn(List.of(later, mine, earliest));
        givenActiveMemberCount(10L, 3);

        service.withdraw(1L, true);

        assertThat(earliest.getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(later.getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(mine.getRole()).isNotEqualTo(ProjectRole.OWNER);
        assertThat(mine.getStatus()).isEqualTo(MemberStatus.EXIT);
        // 후임자가 있으므로 프로젝트는 살아남는다.
        verify(projectPurgeService, never()).purge(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    @DisplayName("합류 시각이 같으면 id가 작은 멤버가 새 OWNER가 된다")
    void breaksJoinTimeTieByLowerId() {
        Project project = project(10L);
        LocalDateTime sameMoment = JOINED_AT.plusDays(1);
        ProjectMember mine = member(100L, project, ProjectRole.OWNER, MemberStatus.ACTIVE, JOINED_AT);
        ProjectMember higherId = member(302L, project, ProjectRole.MEMBER, MemberStatus.ACTIVE, sameMoment);
        ProjectMember lowerId = member(301L, project, ProjectRole.MEMBER, MemberStatus.ACTIVE, sameMoment);
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of(mine));
        // id가 큰 멤버를 먼저 놓는다 — id 타이브레이크가 없으면 이 행이 그대로 선택되어 테스트가 깨진다.
        given(projectMemberRepository.findAllByProjectId(10L)).willReturn(List.of(higherId, lowerId, mine));
        givenActiveMemberCount(10L, 3);

        service.withdraw(1L, true);

        assertThat(lowerId.getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(higherId.getRole()).isEqualTo(ProjectRole.MEMBER);
    }

    @Test
    @DisplayName("남은 활성 멤버가 없으면 프로젝트를 하위 데이터까지 완전 삭제한다")
    void purgesAndDeletesOwnerlessProject() {
        Project project = project(10L);
        ProjectMember mine = member(100L, project, ProjectRole.OWNER, MemberStatus.ACTIVE, JOINED_AT);
        ProjectMember exited = member(200L, project, ProjectRole.MEMBER, MemberStatus.EXIT, JOINED_AT.plusDays(1));
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of(mine));
        given(projectMemberRepository.findAllByProjectId(10L)).willReturn(List.of(mine, exited));
        // 활성 멤버는 나 하나뿐 → 내가 나가면 0이 된다(EXIT 멤버는 세지 않는다).
        givenActiveMemberCount(10L, 1);

        service.withdraw(1L, true);

        // EXIT 멤버는 후임 후보가 아니다 → 소유권 이전 없음.
        assertThat(exited.getRole()).isEqualTo(ProjectRole.MEMBER);
        assertThat(mine.getStatus()).isEqualTo(MemberStatus.EXIT);
        // 멤버 상태를 flush한 뒤 purge → delete 순서여야 한다(purge가 지운 행에 UPDATE가 뒤따르면 실패).
        InOrder inOrder = inOrder(projectMemberRepository, projectPurgeService, projectRepository);
        inOrder.verify(projectMemberRepository).flush();
        inOrder.verify(projectPurgeService).purge(10L);
        inOrder.verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("일반 MEMBER인 프로젝트는 소유권 이전도 삭제도 하지 않는다")
    void plainMemberProjectIsLeftAlone() {
        Project project = project(10L);
        ProjectMember mine = member(100L, project, ProjectRole.MEMBER, MemberStatus.ACTIVE, JOINED_AT);
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of(mine));
        // 나 말고도 활성 멤버(최소한 OWNER)가 남아있는 프로젝트다 → 삭제 대상이 아니다.
        givenActiveMemberCount(10L, 2);

        service.withdraw(1L, true);

        assertThat(mine.getStatus()).isEqualTo(MemberStatus.EXIT);
        assertThat(mine.getRole()).isEqualTo(ProjectRole.MEMBER);
        verify(projectMemberRepository, never()).findAllByProjectId(anyLong());
        verify(projectMemberRepository, never()).flush();
        verify(projectPurgeService, never()).purge(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    @DisplayName("탈퇴하면 모든 활성 멤버십이 EXIT가 된다")
    void allActiveMembershipsBecomeExit() {
        Project owned = project(10L);
        Project joined = project(11L);
        Project past = project(12L);
        ProjectMember ownedMembership = member(100L, owned, ProjectRole.OWNER, MemberStatus.ACTIVE, JOINED_AT);
        ProjectMember successor = member(101L, owned, ProjectRole.MEMBER, MemberStatus.ACTIVE, JOINED_AT.plusDays(1));
        ProjectMember joinedMembership = member(110L, joined, ProjectRole.MEMBER, MemberStatus.ACTIVE, JOINED_AT);
        ProjectMember alreadyExited = member(120L, past, ProjectRole.MEMBER, MemberStatus.EXIT, JOINED_AT);
        given(projectMemberRepository.findAllByUserId(1L))
                .willReturn(List.of(ownedMembership, joinedMembership, alreadyExited));
        given(projectMemberRepository.findAllByProjectId(10L)).willReturn(List.of(ownedMembership, successor));
        // 두 프로젝트 모두 내가 나가도 활성 멤버가 남는다 → 삭제 대상 없음.
        // 이미 EXIT였던 12번은 활성 멤버 수를 세지도 않는다(내 탈퇴로 줄어들 수 없으므로).
        givenActiveMemberCount(10L, 2);
        givenActiveMemberCount(11L, 2);

        service.withdraw(1L, true);

        assertThat(ownedMembership.getStatus()).isEqualTo(MemberStatus.EXIT);
        assertThat(joinedMembership.getStatus()).isEqualTo(MemberStatus.EXIT);
        assertThat(alreadyExited.getStatus()).isEqualTo(MemberStatus.EXIT);
        // 남은 멤버가 있는 프로젝트는 지우지 않는다.
        assertThat(successor.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(successor.getRole()).isEqualTo(ProjectRole.OWNER);
        assertThat(user.isWithdrawn()).isTrue();
        verify(projectPurgeService, never()).purge(anyLong());
    }

    @Test
    @DisplayName("파기 배치는 조회된 대상만 익명화하고 각각 다른 비밀값을 인코딩한다")
    void purgeExpiredAnonymizesOnlyReturnedTargets() {
        User first = withdrawnUser(11L, "first@plog.test");
        User second = withdrawnUser(12L, "second@plog.test");
        User untouched = withdrawnUser(13L, "untouched@plog.test");
        given(passwordEncoder.encode(anyString())).willAnswer(call -> "encoded:" + call.getArgument(0));
        given(userRepository.findAllByDeletedAtBeforeAndAnonymizedAtIsNull(any(LocalDateTime.class)))
                .willReturn(List.of(first, second));

        // 서비스 호출 전후 시각을 떠서 threshold = now - retention(7일) 창을 만든다.
        // 고정 오차값 대신 실제 호출 구간으로 브래킷을 잡아야 느린 테스트 실행에도 흔들리지 않는다.
        LocalDateTime beforeCall = TimeUtil.nowUtc();
        int purged = service.purgeExpired();
        LocalDateTime afterCall = TimeUtil.nowUtc();

        assertThat(purged).isEqualTo(2);
        assertThat(first.getAnonymizedAt()).isNotNull();
        assertThat(second.getAnonymizedAt()).isNotNull();
        // 익명화 값은 "id + 임의값" 형식이다. id만 쓰면 살아있는 유저가 미리 선점할 수 있는 값이라
        // 파기 시점에 uk_user_email / uk_user_nickname 위반으로 파기가 영구히 막힌다.
        // 형식을 정규식으로 못 박아 임의값이 빠지거나 길이가 줄어들면 실패하게 한다.
        assertThat(first.getEmail()).matches(ANONYMIZED_EMAIL_PATTERN.formatted(11));
        assertThat(second.getEmail()).matches(ANONYMIZED_EMAIL_PATTERN.formatted(12));
        assertThat(first.getNickname()).matches(ANONYMIZED_NICKNAME_PATTERN.formatted(11));
        assertThat(second.getNickname()).matches(ANONYMIZED_NICKNAME_PATTERN.formatted(12));
        assertThat(first.getName()).matches(ANONYMIZED_NICKNAME_PATTERN.formatted(11));
        // 같은 배치 안에서도 임의값이 재사용되지 않는다.
        assertThat(suffixOf(first.getNickname())).isNotEqualTo(suffixOf(second.getNickname()));
        // 대상별로 서로 다른 임의 비밀값을 인코딩해야 한다(공용 값 재사용 금지).
        assertThat(first.getPassword()).startsWith("encoded:");
        assertThat(second.getPassword()).startsWith("encoded:");
        assertThat(first.getPassword()).isNotEqualTo(second.getPassword());
        // 조회되지 않은 계정은 그대로다.
        assertThat(untouched.getAnonymizedAt()).isNull();
        assertThat(untouched.getEmail()).isEqualTo("untouched@plog.test");

        ArgumentCaptor<String> secrets = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder, times(2)).encode(secrets.capture());
        assertThat(secrets.getAllValues()).doesNotHaveDuplicates();

        // 파기 기준 시각 = now - retention(7일). 호출 전후 시각으로 창을 잡아 retention 값 자체를 검증한다
        // (예: 30일·365일로 잘못 설정돼도 이 창을 벗어나므로 실패한다) — 절대 오차값이 아니라 실제 호출 구간을 쓰므로
        // 느린 테스트 실행에도 흔들리지 않는다.
        ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).findAllByDeletedAtBeforeAndAnonymizedAtIsNull(threshold.capture());
        assertThat(threshold.getValue()).isBetween(beforeCall.minus(RETENTION), afterCall.minus(RETENTION));
    }

    @Test
    @DisplayName("파기 대상이 없으면 0을 반환한다")
    void purgeExpiredReturnsZeroWithoutTargets() {
        given(userRepository.findAllByDeletedAtBeforeAndAnonymizedAtIsNull(any(LocalDateTime.class)))
                .willReturn(List.of());

        assertThat(service.purgeExpired()).isZero();
    }

    @Test
    @DisplayName("소셜 가입 계정은 파기 시 password가 아니라 providerId를 익명화한다")
    void purgeExpiredAnonymizesProviderIdForSocialUser() {
        // User.anonymize는 XOR 제약(password 또는 provider) 때문에 가입 수단별로 다른 필드를 덮는다.
        // 기존 파기 테스트는 전부 로컬 가입 사용자만 다뤄 이 분기(소셜 경로)가 검증되지 않았다.
        User social = User.createSocial("social@plog.test", "홍길동", "바나나",
                ProviderType.KAKAO, "kakao-raw-id");
        ReflectionTestUtils.setField(social, "id", 21L);
        social.withdraw(JOINED_AT);
        given(passwordEncoder.encode(anyString())).willReturn("encoded-unused");
        given(userRepository.findAllByDeletedAtBeforeAndAnonymizedAtIsNull(any(LocalDateTime.class)))
                .willReturn(List.of(social));

        int purged = service.purgeExpired();

        assertThat(purged).isEqualTo(1);
        assertThat(social.getEmail()).matches(ANONYMIZED_EMAIL_PATTERN.formatted(21));
        assertThat(social.getProviderId()).matches("withdrawn-21-[0-9a-f]{12}");
        // 소셜 사용자는 애초에 password 컬럼을 쓰지 않는다 — 파기 후에도 null이어야 한다(엉뚱한 자리에 비밀값이 새면 안 됨).
        assertThat(social.getPassword()).isNull();
        assertThat(social.getAnonymizedAt()).isNotNull();
    }

    @Test
    @DisplayName("OWNER가 아니어도 내가 마지막 활성 멤버였다면 프로젝트를 완전 삭제한다")
    void purgesProjectWhenLastActiveMemberIsNotOwner() {
        // ProjectLeaveService.leave에는 OWNER 가드가 없다 → OWNER가 먼저 leave로 나가면
        // "활성 멤버는 있는데 OWNER는 없는" 프로젝트가 만들어진다. 그 프로젝트에 남은 MEMBER가 탈퇴하는 경우다.
        // 역할(OWNER)로만 판정하면 아무도 없는 프로젝트가 살아남고, 프로젝트 도메인은 deleted_at을 보지 않으므로
        // 초대 링크를 가진 사람이 다시 들어와 되살릴 수 있다.
        Project project = project(10L);
        ProjectMember mine = member(100L, project, ProjectRole.MEMBER, MemberStatus.ACTIVE, JOINED_AT);
        ProjectMember exitedOwner = member(200L, project, ProjectRole.OWNER, MemberStatus.EXIT, JOINED_AT.minusDays(1));
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of(mine));
        given(projectMemberRepository.findAllByProjectId(10L)).willReturn(List.of(mine, exitedOwner));
        givenActiveMemberCount(10L, 1);

        service.withdraw(1L, true);

        assertThat(mine.getStatus()).isEqualTo(MemberStatus.EXIT);
        // leave 엔드포인트와 같은 처리: 멤버 상태 flush → 하위 데이터 purge → 프로젝트 삭제.
        InOrder inOrder = inOrder(projectMemberRepository, projectPurgeService, projectRepository);
        inOrder.verify(projectMemberRepository).flush();
        inOrder.verify(projectPurgeService).purge(10L);
        inOrder.verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("후임 없는 OWNER의 프로젝트도 purge와 delete는 한 번만 실행한다")
    void ownerlessProjectIsPurgedOnlyOnce() {
        // "후임 없는 OWNER"와 "남은 활성 멤버 0"은 같은 프로젝트를 가리킨다.
        // 두 조건을 각각 삭제 경로로 두면 같은 프로젝트를 두 번 purge하게 되므로 판정 지점은 하나여야 한다.
        Project project = project(10L);
        ProjectMember mine = member(100L, project, ProjectRole.OWNER, MemberStatus.ACTIVE, JOINED_AT);
        given(projectMemberRepository.findAllByUserId(1L)).willReturn(List.of(mine));
        given(projectMemberRepository.findAllByProjectId(10L)).willReturn(List.of(mine));
        givenActiveMemberCount(10L, 1);

        service.withdraw(1L, true);

        verify(projectPurgeService, times(1)).purge(10L);
        verify(projectRepository, times(1)).delete(project);
    }

    @Test
    @DisplayName("탈퇴하면 그 이메일의 인증 행을 목적 무관하게 전부 지운다")
    void withdrawDeletesEmailVerificationRows() {
        // email_verification 행은 가입/재설정 성공 시에만 소비되고 TTL 스위퍼도 없다.
        // 재설정 코드만 받고 흐름을 버린 뒤 탈퇴하면 실제 이메일이 그 표에 무기한 남는다(파기 배치는 tb_user만 건드림).
        service.withdraw(1L, true);

        verify(emailVerificationCodeService).deleteAllByEmail("a@plog.test");
    }

    @Test
    @DisplayName("한 건이 실패해도 배치는 나머지를 계속 파기하고 실패한 userId를 ERROR로 남긴다")
    void purgeExpiredIsolatesFailingRow() {
        // 살아있는 유저가 "탈퇴한사용자-{id}"를 선점했을 때처럼 한 행이 유니크 위반으로 실패하는 상황.
        // 배치 전체가 한 트랜잭션이면 이 한 행 때문에 매일 전량 롤백되어 어떤 계정도 영구히 파기되지 않는다.
        User failing = withdrawnUser(31L, "failing@plog.test");
        User survivor = withdrawnUser(32L, "survivor@plog.test");
        given(passwordEncoder.encode(anyString())).willReturn("encoded-secret");
        given(userRepository.findAllByDeletedAtBeforeAndAnonymizedAtIsNull(any(LocalDateTime.class)))
                .willReturn(List.of(failing, survivor));
        given(userRepository.saveAndFlush(failing))
                .willThrow(new DataIntegrityViolationException("uk_user_nickname"));

        ListAppender<ILoggingEvent> logs = attachAppender(UserWithdrawalService.class);
        int purged;
        try {
            purged = service.purgeExpired();
        } finally {
            detachAppender(UserWithdrawalService.class, logs);
        }

        // 실패한 1건은 세지 않고, 뒤따르는 행은 그대로 파기된다(= 실패가 배치를 오염시키지 않는다).
        assertThat(purged).isEqualTo(1);
        verify(userRepository).saveAndFlush(survivor);
        assertThat(survivor.getAnonymizedAt()).isNotNull();
        assertThat(survivor.getEmail()).matches(ANONYMIZED_EMAIL_PATTERN.formatted(32));
        // 실패는 반드시 보이게 남는다 — 어느 계정인지 없으면 수동 조치가 불가능하므로 userId를 포함해야 한다.
        assertThat(logs.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains("31");
                    assertThat(event.getThrowableProxy()).isNotNull();
                });
    }

    /** 프로젝트에 남아있는(내 몫 포함) 활성 멤버 수. 서비스는 이 값이 1 이하일 때만 프로젝트를 삭제한다. */
    private void givenActiveMemberCount(long projectId, long activeCount) {
        given(projectMemberRepository.countByProjectIdAndStatus(projectId, MemberStatus.ACTIVE))
                .willReturn(activeCount);
    }

    /** "탈퇴한사용자-{id}-{임의값}"에서 임의값만 떼어낸다. */
    private String suffixOf(String anonymizedNickname) {
        return anonymizedNickname.substring(anonymizedNickname.lastIndexOf('-') + 1);
    }

    private ListAppender<ILoggingEvent> attachAppender(Class<?> loggerOwner) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(loggerOwner)).addAppender(appender);
        return appender;
    }

    private void detachAppender(Class<?> loggerOwner, ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(loggerOwner)).detachAppender(appender);
        appender.stop();
    }

    private Project project(long id) {
        return Project.builder()
                .id(id)
                .projectName("Plog " + id)
                .inviteTokenHash("hash-" + id)
                .inviteTokenEncrypted("encrypted-" + id)
                .projectType(ProjectType.DEVELOP)
                .status(ProjectStatus.IN_PROGRESS)
                .startDay(LocalDate.of(2026, 7, 1))
                .endDay(LocalDate.of(2026, 8, 31))
                .build();
    }

    private ProjectMember member(long id, Project project, ProjectRole role, MemberStatus status,
                                 LocalDateTime createdAt) {
        ProjectMember member = ProjectMember.builder()
                .id(id)
                .project(project)
                .role(role)
                .status(status)
                .build();
        // createdAt은 JPA Auditing이 채우므로 영속화하지 않은 엔티티에는 리플렉션으로 넣는다(ProjectJoinServiceTest와 동일).
        ReflectionTestUtils.setField(member, "createdAt", createdAt);
        ReflectionTestUtils.setField(member, "updatedAt", createdAt);
        return member;
    }

    private User withdrawnUser(long id, String email) {
        User withdrawn = User.createLocal(email, "encoded", "홍길동" + id, "닉네임" + id, ProfilePreset.OTTER);
        // id는 IDENTITY 전략으로 DB가 채우므로 리플렉션으로 넣는다(anonymize가 id를 문자열에 쓴다).
        ReflectionTestUtils.setField(withdrawn, "id", id);
        withdrawn.withdraw(JOINED_AT);
        return withdrawn;
    }
}
