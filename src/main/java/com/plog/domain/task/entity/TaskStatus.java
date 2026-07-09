package com.plog.domain.task.entity;

// TODO(팀 확인): ERD에 "2개? 3개?"로 미확정. 리포트의 "진행중" 표현과
//  활동 흐름 그래프를 고려해 3단계로 세팅. 2개로 확정되면 IN_PROGRESS 제거.
public enum TaskStatus {
    TODO, IN_PROGRESS, DONE
}
