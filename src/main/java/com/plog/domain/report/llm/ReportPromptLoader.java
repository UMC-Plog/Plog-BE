package com.plog.domain.report.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 시스템 프롬프트 로딩. 프롬프트는 코드 상수가 아니라 {@code resources/prompts/*.txt} 에 둔다 —
 * 문구 수정이 잦고, 기획·디자인 쪽과 문구를 맞출 때 자바 파일을 열게 하고 싶지 않다.
 * <p>
 * 기동 시 한 번 읽어 필드에 들고 있는다. 매 호출 파일을 읽으면 리포트 1건에 멤버 수만큼
 * 디스크 접근이 생긴다.
 */
@Component
public class ReportPromptLoader {

    private static final String MEMBER_PROMPT_PATH = "prompts/report-member.system.txt";
    private static final String TEAM_PROMPT_PATH = "prompts/report-team.system.txt";

    private final String memberSystemPrompt;
    private final String teamSystemPrompt;

    public ReportPromptLoader() {
        this.memberSystemPrompt = read(MEMBER_PROMPT_PATH);
        this.teamSystemPrompt = read(TEAM_PROMPT_PATH);
    }

    public String memberSystemPrompt() {
        return memberSystemPrompt;
    }

    public String teamSystemPrompt() {
        return teamSystemPrompt;
    }

    private String read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (var input = resource.getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (content.isBlank()) {
                // 프롬프트가 비면 LLM 이 규칙 없이 자유 서술을 하게 된다 — 조용히 넘어가면 안 된다.
                throw new IllegalStateException("프롬프트 파일이 비어 있습니다: " + path);
            }
            return content;
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일을 읽지 못했습니다: " + path, e);
        }
    }
}
