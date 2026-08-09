package com.plog.domain.report.service;

import com.plog.domain.report.entity.ActivityCategory;
import java.util.List;
import java.util.Map;

/**
 * 카테고리별 대표(anchor) 문장. 실측 데이터 없이 PM 논의 때 잡은 초안이다 — 리포트가 실제로
 * 나온 뒤 분류 결과를 보고 재조정될 수 있고, 그때는 이 클래스만 고치면 된다(호출부 영향 없음).
 * <p>
 * {@link ActivityAnchorCache}가 이 문장들을 임베딩해서 카테고리별 centroid로 캐싱한다.
 */
final class ActivityAnchors {

    private ActivityAnchors() {
    }

    static final Map<ActivityCategory, List<String>> SENTENCES = Map.of(
            ActivityCategory.DECISION, List.of(
                    "이걸로 확정하겠습니다",
                    "다들 동의하시면 이 방향으로 진행할게요",
                    "A안으로 결정했습니다",
                    "이 방식으로 가는 걸로 정리하겠습니다",
                    "최종적으로 이렇게 하기로 했어요"
            ),
            ActivityCategory.PROBLEM_SOLVING, List.of(
                    "에러 원인을 찾았습니다",
                    "버그를 수정했어요",
                    "이슈가 해결됐습니다",
                    "충돌 문제를 해결했습니다",
                    "원인 파악해서 고쳤습니다"
            ),
            ActivityCategory.SCHEDULE_COORDINATION, List.of(
                    "마감일을 조정해도 될까요",
                    "회의 시간 다시 잡을게요",
                    "일정표 업데이트했습니다",
                    "역할 분담을 다시 정리했어요",
                    "이번 주까지로 미룰 수 있을까요"
            ),
            ActivityCategory.FEEDBACK, List.of(
                    "이 부분은 이렇게 수정하면 좋겠어요",
                    "리뷰 남겼습니다",
                    "여기 조금 아쉬운 것 같아요",
                    "개선하면 좋을 것 같은 부분이 있어요",
                    "이 로직은 이렇게 바꾸는 게 나을 것 같습니다"
            ),
            ActivityCategory.DELIVERABLE_SUBMIT, List.of(
                    "발표자료 첨부했습니다",
                    "작업물 공유드려요",
                    "파일 업로드했습니다",
                    "완성본 올렸습니다",
                    "산출물 제출합니다"
            ),
            ActivityCategory.SIMPLE_RESPONSE, List.of(
                    "네 확인했습니다",
                    "알겠습니다",
                    "넵 그렇게 할게요",
                    "확인했어요",
                    "좋습니다 진행할게요"
            )
    );
}