package com.plog.domain.report.service;

/**
 * {@link ActivityContentRefiner}의 정제 결과.
 *
 * @param cleanContent 정제된 텍스트. 노이즈로 판정됐거나 원본에 텍스트가 없으면 null
 * @param noise        2~3단계(분류/업무카드 연결) 및 임베딩 생성 대상에서 제외해야 하면 true.
 *                     원본에 content 자체가 없는 이벤트(예: TASK_STATUS_CHANGE)는 노이즈가 아니라
 *                     "정제할 텍스트가 없는" 경우라 false다 — 노이즈와는 의미가 다르다.
 */
public record RefinedContent(String cleanContent, boolean noise) {

    public boolean hasCleanContent() {
        return cleanContent != null && !cleanContent.isBlank();
    }
}