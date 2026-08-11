package com.plog.domain.task.service;

import com.plog.domain.report.entity.CompetencyCategory;
import java.util.List;
import java.util.Map;

/** 업무 카드 제목 분류 전용 anchor 문장. 활동 로그 anchor와 문체가 다르므로 별도로 관리한다. */
final class TaskCompetencyAnchors {

    private TaskCompetencyAnchors() {
    }

    static final Map<CompetencyCategory, List<String>> SENTENCES = Map.of(
            CompetencyCategory.COLLABORATION, List.of(
                    "회의 일정 조율", "팀 역할 분담 및 진행 상황 관리", "팀원 간 협업 프로세스 정리",
                    "공동 작업 일정 관리", "부서 간 업무 조율"),
            CompetencyCategory.LEADERSHIP, List.of(
                    "기술 스택 선정", "프로젝트 개발 방향 결정", "주요 의사결정 및 팀 리딩",
                    "요구사항 우선순위 결정", "프로젝트 진행 전략 수립"),
            CompetencyCategory.COMMUNICATION, List.of(
                    "팀원 코드 리뷰 및 피드백", "고객 요구사항 인터뷰", "회의 내용 공유 및 의견 정리",
                    "팀 피드백 취합", "프로젝트 진행 상황 공유"),
            CompetencyCategory.OUTPUT, List.of(
                    "로그인 API 구현", "발표 자료 제작", "디자인 시안 완성", "테스트 코드 작성",
                    "최종 산출물 제작")
    );
}
