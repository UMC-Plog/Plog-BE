package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class ActivityRefinementServiceTest {

    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private ChatMessageRepository chatMessageRepository;

    private ActivityRefinementService service;

    @BeforeEach
    void setUp() {
        service = new ActivityRefinementService(activityLogRepository, chatMessageRepository);
    }

    private ReportActivityLog chatLog(ProjectMember member, String content, LocalDateTime occurredAt) {
        return ReportActivityLog.create(
                member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, content, occurredAt, null, null);
    }

    /** 테스트 전용: id는 IDENTITY 생성이라 create()로는 채울 수 없어 리플렉션으로 강제 주입한다. */
    private void assignId(ReportActivityLog activity, Long id) {
        try {
            Field field = ReportActivityLog.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(activity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubFetch(List<ReportActivityLog> logs) {
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNullOrderByOccurredAtAscIdAsc(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(logs);
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        stubFetch(List.of());

        int refined = service.refineNoiseBatch();

        assertThat(refined).isZero();
    }

    @Test
    void 단독_감탄사는_노이즈로_확정한다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = chatLog(member, "음", LocalDateTime.of(2026, 8, 7, 10, 0));
        assignId(log, 100L);
        stubFetch(List.of(log));

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isTrue();
        assertThat(log.getContent()).isEqualTo("음"); // 원본은 절대 건드리지 않는다
    }

    @Test
    void 업무_문장은_노이즈가_아니다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = chatLog(member, "채팅 첨부파일 업로드 API 구현했습니다", LocalDateTime.now());
        assignId(log, 101L);
        stubFetch(List.of(log));

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isFalse();
    }

    @Test
    void 채팅_원문을_로그에_저장하지_않고_원본에서_읽어_정제한다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = ReportActivityLog.create(
                member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, null,
                LocalDateTime.now(), null, "chat:77");
        assignId(log, 102L);
        ChatMessage message = mock(ChatMessage.class);
        when(message.getMessage()).thenReturn("인증 오류 원인을 분석하고 해결 방안을 공유했습니다");
        when(chatMessageRepository.findById(77L)).thenReturn(java.util.Optional.of(message));
        stubFetch(List.of(log));

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isFalse();
        assertThat(log.getContent()).isNull();
    }

    @Test
    void 같은_배치_안에서_같은_회원의_동일_내용_반복_메시지는_먼저_온_것만_살린다() {
        // 리포지토리가 이미 occurredAt/id 오름차순으로 정렬해 준다는 계약이므로,
        // 테스트도 그 정렬된 순서 그대로 스텁한다(서비스는 더 이상 재정렬하지 않는다).
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog first = chatLog(member, "테스트 완료했어요", LocalDateTime.of(2026, 8, 7, 9, 0));
        assignId(first, 200L);
        ReportActivityLog duplicate = chatLog(member, "테스트 완료했어요", LocalDateTime.of(2026, 8, 7, 9, 1));
        assignId(duplicate, 201L);
        stubFetch(List.of(first, duplicate));

        service.refineNoiseBatch();

        assertThat(first.isNoise()).isFalse();
        assertThat(duplicate.isNoise()).isTrue();
    }

    @Test
    void 서로_다른_회원의_동일_내용은_중복이_아니다() {
        ProjectMember memberA = ProjectMember.builder().id(1L).build();
        ProjectMember memberB = ProjectMember.builder().id(2L).build();
        ReportActivityLog logA = chatLog(memberA, "확인했습니다 진행할게요", LocalDateTime.of(2026, 8, 7, 9, 0));
        assignId(logA, 300L);
        ReportActivityLog logB = chatLog(memberB, "확인했습니다 진행할게요", LocalDateTime.of(2026, 8, 7, 9, 1));
        assignId(logB, 301L);
        stubFetch(List.of(logA, logB));

        service.refineNoiseBatch();

        assertThat(logA.isNoise()).isFalse();
        assertThat(logB.isNoise()).isFalse();
    }

    @Test
    void 텍스트가_없는_이벤트끼리는_중복으로_묶이지_않는다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog statusChangeA = ReportActivityLog.create(
                member, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.of(2026, 8, 7, 9, 0), null, null);
        assignId(statusChangeA, 400L);
        ReportActivityLog statusChangeB = ReportActivityLog.create(
                member, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.of(2026, 8, 7, 9, 1), null, null);
        assignId(statusChangeB, 401L);
        stubFetch(List.of(statusChangeA, statusChangeB));

        service.refineNoiseBatch();

        assertThat(statusChangeA.isNoise()).isFalse();
        assertThat(statusChangeB.isNoise()).isFalse();
    }

    @Test
    void projectMember가_없는_로그는_내용이_같아도_중복_판정에서_제외한다() {
        // 외부 계정 매핑이 안 된 시점에 수집된 활동 — 서로 다른 실제 주체일 수 있어
        // null을 키에 넣어 묶어버리면 안 된다.
        ReportActivityLog unmappedA = chatLog(null, "동일한 메시지", LocalDateTime.of(2026, 8, 7, 9, 0));
        assignId(unmappedA, 500L);
        ReportActivityLog unmappedB = chatLog(null, "동일한 메시지", LocalDateTime.of(2026, 8, 7, 9, 1));
        assignId(unmappedB, 501L);
        stubFetch(List.of(unmappedA, unmappedB));

        service.refineNoiseBatch();

        assertThat(unmappedA.isNoise()).isFalse();
        assertThat(unmappedB.isNoise()).isFalse();
    }

    @Test
    void 이전_배치에서_이미_확정된_동일_원문이_있으면_배치_경계를_넘어도_중복으로_판정한다() {
        // 이번 배치에는 한 건만 들어오지만, 그 전 배치(이미 noiseFiltered=false로 확정된 행)에
        // 같은 원문이 있다고 가정 — DB 조회로만 판정 가능한 케이스.
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = chatLog(member, "이전 배치와 동일한 메시지", LocalDateTime.of(2026, 8, 7, 9, 0));
        assignId(log, 900L);
        stubFetch(List.of(log));
        when(activityLogRepository.existsByProjectMember_IdAndSourceDomainAndContentAndNoiseFilteredFalseAndIdLessThan(
                eq(1L), eq(SourceDomain.CHAT), eq("이전 배치와 동일한 메시지"), eq(900L)))
                .thenReturn(true);

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isTrue();
    }

    @Test
    void 이전_배치에_동일_원문이_없으면_노이즈로_판정하지_않는다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = chatLog(member, "완전히 새로운 메시지", LocalDateTime.of(2026, 8, 7, 9, 0));
        assignId(log, 901L);
        stubFetch(List.of(log));
        when(activityLogRepository.existsByProjectMember_IdAndSourceDomainAndContentAndNoiseFilteredFalseAndIdLessThan(
                anyLong(), ArgumentMatchers.any(SourceDomain.class), ArgumentMatchers.anyString(), anyLong()))
                .thenReturn(false);

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isFalse();
    }
}
