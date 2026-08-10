package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.plog.domain.evaluation.entity.PeerEvaluation;
import com.plog.domain.evaluation.entity.SelfFeedback;
import com.plog.domain.evaluation.repository.PeerEvaluationRepository;
import com.plog.domain.evaluation.repository.SelfFeedbackRepository;
import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.project.repository.ProjectMemberRepository;
import com.plog.domain.project.repository.ProjectRepository;
import com.plog.domain.report.entity.Report;
import com.plog.domain.report.entity.ReportMemberResult;
import com.plog.domain.report.entity.ReportStatus;
import com.plog.domain.report.llm.MemberReportText;
import com.plog.domain.report.llm.ReportLlmGateway;
import com.plog.domain.report.llm.TeamReportText;
import com.plog.domain.report.repository.ReportMemberResultRepository;
import com.plog.domain.report.repository.ReportRepository;
import com.plog.domain.task.entity.Task;
import com.plog.domain.task.entity.TaskCategory;
import com.plog.domain.task.entity.TaskStatus;
import com.plog.domain.task.repository.TaskRepository;
import com.plog.e2e.support.E2eTestBase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 리포트 파이프라인 0~6단계를 실제 빈(Testcontainers Postgres, {@link E2eTestBase})으로 끝까지
 * 돌려보는 통합 테스트.
 * <p>
 * 처음엔 자체적으로 {@code @SpringBootTest} + {@code @Testcontainers}를 붙였다가 JWT/CORS/S3
 * 등 필수 설정값이 없어 컨텍스트 기동 자체가 실패했다 — 이미 이 문제를 해결해 둔
 * {@link E2eTestBase}(JWT secret, invite key, S3/FCM mock 등 전부 세팅됨)를 상속하는 걸로
 * 바꿨다. 직접 만들지 말고 기존 걸 재사용할 것.
 * <p>
 * <b>LLM만 {@code @MockitoBean}으로 막는다</b> — {@link ReportLlmGateway}는 실제 Gemini 호출이라
 * 네트워크/API 키 없이 CI에서 못 돌린다. 임베딩은 {@code GEMINI_API_KEY}가 없으면
 * {@code EmbeddingClientConfig}가 자동으로 StubEmbeddingClient로 폴백하므로 별도 설정이 필요 없다
 * (벡터가 의미는 없지만 파이프라인이 끝까지 도는지 검증하는 데는 지장 없음).
 * <p>
 * <b>0단계는 이벤트 경유가 아니라 서비스 직접 호출로 시딩한다.</b> 이벤트 발행→
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 배선은 {@code TaskActivityLogServiceTest}/
 * {@code TaskActivityLogListenerTest} 등 유닛 테스트가 이미 검증했고, 이 테스트는 "실 데이터가
 * 1~6단계를 예외 없이 통과하는지"에만 집중한다.
 */
@DisplayName("리포트 파이프라인 통합 테스트")
class ReportPipelineIntegrationTest extends E2eTestBase {

    @MockitoBean
    private ReportLlmGateway llmGateway;

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PeerEvaluationRepository peerEvaluationRepository;
    @Autowired private SelfFeedbackRepository selfFeedbackRepository;

    @Autowired private TaskActivityLogService taskActivityLogService;
    @Autowired private ActivityRefinementService refinementService;
    @Autowired private ActivityEmbeddingService embeddingService;
    @Autowired private ActivityClassificationService classificationService;

    @Autowired private ReportRepository reportRepository;
    @Autowired private ReportMemberResultRepository reportMemberResultRepository;
    @Autowired private ReportGenerationService reportGenerationService;

    @Test
    @DisplayName("리포트 한 건이 예외 없이 끝까지 발행된다")
    void 리포트_한_건이_예외_없이_끝까지_발행된다() {
        // ── given: 프로젝트 2명(오너/멤버) + 멤버 쪽 업무카드/평가 시딩 ──
        // saveUser/saveProject/saveMember는 E2eTestBase의 raw-jdbc 헬퍼(Long id만 돌려줌).
        Long projectId = saveProject("pipeline");
        Long ownerUserId = saveUser("pipeline-owner");
        Long memberUserId = saveUser("pipeline-member");
        Long ownerId = saveMember(ownerUserId, projectId, "OWNER", "ACTIVE", "오너");
        Long memberId = saveMember(memberUserId, projectId, "MEMBER", "ACTIVE", "멤버");

        ProjectMember owner = projectMemberRepository.getReferenceById(ownerId);
        ProjectMember member = projectMemberRepository.getReferenceById(memberId);

        // 완료 처리된 업무카드 하나 + 0단계 활동 로그 직접 적재(TASK_STATUS_CHANGE)
        Task task = taskRepository.save(Task.create(
                member, "리포트 API 구현", TaskCategory.DEVELOP, TaskStatus.DONE,
                LocalDate.of(2026, 8, 1)));
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 1, 10, 0);
        taskActivityLogService.collectStatusChanged(
                task.getId(), memberId, TaskStatus.TODO, TaskStatus.DONE, occurredAt);

        // 오너→멤버 Peer 평가 1건 + 멤버 자기 피드백 1건. 오너는 의도적으로 평가도 못 받고
        // 자기 피드백도 안 낸 상태로 둔다 — PeerEvaluationSummary.none()/SelfFeedbackMatchSummary
        // .notSubmitted()의 normalizedScore가 null이 아니라 0인지(직전 버그 수정)를 이 케이스로 검증한다.
        peerEvaluationRepository.save(PeerEvaluation.builder()
                .evaluator(owner).evaluatee(member)
                .collaborationScore(4).initiativeScore(4).communicationScore(4).outputScore(4)
                .keywords(List.of("성실함"))
                .feedback("담당 업무를 기한 내에 잘 마무리했습니다.")
                .build());
        selfFeedbackRepository.save(SelfFeedback.builder()
                .projectMember(member)
                .content("리포트 API 구현을 기한 내에 완료했습니다.")
                .build());

        // ── when: 1→3→2단계 배치로 활동 로그를 끝까지 처리한 뒤 4~6단계 오케스트레이션 실행 ──
        drainBatch(refinementService::refineNoiseBatch);
        drainBatch(embeddingService::embedBatch);
        drainBatch(classificationService::classifyBatch);

        stubLlmGateway();

        Report report = reportRepository.save(Report.start(projectRepository.findById(projectId).orElseThrow()));
        ReportGenerationResult result = reportGenerationService.generate(report.getId());

        // ── then ──
        assertThat(result.published()).isTrue();
        assertThat(result.textSucceeded()).isEqualTo(2);

        Report saved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.COMPLETED);

        // 이번에 고친 두 버그(internalScore null, peer/self null)가 실제로 안 터지는지의 최종 확인 —
        // 평가/피드백이 하나도 없는 오너 쪽 결과에서 특히 중요하다.
        ReportMemberResult ownerResult = reportMemberResultRepository
                .findByReportIdAndProjectMemberId(report.getId(), ownerId)
                .orElseThrow();
        assertThat(ownerResult.getInternalScore()).isNotNull();
        assertThat(ownerResult.getPeerScore()).isNotNull();
        assertThat(ownerResult.getSelfFeedbackScore()).isNotNull();
        assertThat(ownerResult.getFinalScore()).isNotNull();

        ReportMemberResult memberResult = reportMemberResultRepository
                .findByReportIdAndProjectMemberId(report.getId(), memberId)
                .orElseThrow();
        assertThat(memberResult.getInternalScore()).isNotNull();
        assertThat(memberResult.getPeerScore()).isNotNull();
        assertThat(memberResult.getSelfFeedbackScore()).isNotNull();
        assertThat(memberResult.getFinalScore()).isNotNull();
        assertThat(memberResult.getTotalTaskCount()).isEqualTo(1);
        assertThat(memberResult.getCompletedTaskCount()).isEqualTo(1);
    }

    /** 배치 limit보다 데이터가 적을 걸 알지만, 나중에 시딩 데이터가 늘어나도 안전하게 소진될 때까지 반복. */
    private void drainBatch(IntSupplier batchCall) {
        int processed;
        do {
            processed = batchCall.getAsInt();
        } while (processed > 0);
    }

    private void stubLlmGateway() {
        MemberReportText memberText = new MemberReportText(
                "기한 내 완료율이 높고 협업 활동이 꾸준합니다.", List.of(), null, null, null);
        when(llmGateway.generateMemberText(any()))
                .thenReturn(new ReportLlmGateway.GeneratedMemberText(memberText, "{}", "stub-model"));
        when(llmGateway.generateTeamText(any()))
                .thenReturn(new TeamReportText("팀 전체적으로 마감을 잘 지켰습니다.", "다음엔 산출물 공유를 더 늘려보세요."));
    }
}