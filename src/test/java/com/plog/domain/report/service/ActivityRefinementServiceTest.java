package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
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

    private ActivityRefinementService service;

    @BeforeEach
    void setUp() {
        service = new ActivityRefinementService(activityLogRepository);
    }

    private ReportActivityLog chatLog(ProjectMember member, String content, LocalDateTime occurredAt) {
        return ReportActivityLog.create(
                member, SourceDomain.CHAT, RawActivityType.CHAT_MESSAGE, content, occurredAt, null, null);
    }

    @Test
    void 대상이_없으면_아무것도_하지_않는다() {
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of());

        int refined = service.refineNoiseBatch();

        assertThat(refined).isZero();
    }

    @Test
    void 단독_감탄사는_노이즈로_확정한다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = chatLog(member, "음", LocalDateTime.of(2026, 8, 7, 10, 0));
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of(log));

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isTrue();
        assertThat(log.getContent()).isEqualTo("음"); // 원본은 절대 건드리지 않는다
    }

    @Test
    void 업무_문장은_노이즈가_아니다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog log = chatLog(member, "채팅 첨부파일 업로드 API 구현했습니다", LocalDateTime.now());
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of(log));

        service.refineNoiseBatch();

        assertThat(log.isNoise()).isFalse();
    }

    @Test
    void 같은_회원의_동일_내용_반복_메시지는_먼저_온_것만_살리고_나머지는_노이즈_처리한다() {
        ProjectMember member = ProjectMember.builder().id(1L).build();
        ReportActivityLog first = chatLog(member, "테스트 완료했어요", LocalDateTime.of(2026, 8, 7, 9, 0));
        ReportActivityLog duplicate = chatLog(member, "테스트 완료했어요", LocalDateTime.of(2026, 8, 7, 9, 1));
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of(duplicate, first)); // 조회 순서가 뒤섞여도 occurredAt 기준으로 정렬돼야 함

        service.refineNoiseBatch();

        assertThat(first.isNoise()).isFalse();
        assertThat(duplicate.isNoise()).isTrue();
    }

    @Test
    void 서로_다른_회원의_동일_내용은_중복이_아니다() {
        ProjectMember memberA = ProjectMember.builder().id(1L).build();
        ProjectMember memberB = ProjectMember.builder().id(2L).build();
        ReportActivityLog logA = chatLog(memberA, "확인했습니다 진행할게요", LocalDateTime.of(2026, 8, 7, 9, 0));
        ReportActivityLog logB = chatLog(memberB, "확인했습니다 진행할게요", LocalDateTime.of(2026, 8, 7, 9, 1));
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of(logA, logB));

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
        ReportActivityLog statusChangeB = ReportActivityLog.create(
                member, SourceDomain.TASK, RawActivityType.TASK_STATUS_CHANGE,
                null, LocalDateTime.of(2026, 8, 7, 9, 1), null, null);
        when(activityLogRepository.findBySourceDomainInAndNoiseFilteredIsNull(
                ArgumentMatchers.anyList(), ArgumentMatchers.any(Limit.class)))
                .thenReturn(List.of(statusChangeA, statusChangeB));

        service.refineNoiseBatch();

        assertThat(statusChangeA.isNoise()).isFalse();
        assertThat(statusChangeB.isNoise()).isFalse();
    }
}