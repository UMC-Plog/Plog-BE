package com.plog.global.common;

import com.plog.global.config.ApiProperties;
import org.springframework.stereotype.Component;

/**
 * 첨부 다운로드 URL 발급 엔드포인트의 절대 URL 을 만든다.
 * <p>
 * 조회 응답의 downloadUrlApi 에 담긴다. 프론트가 경로 패턴을 코드에 박지 않아도 되도록
 * 서버가 완성해서 내려준다.
 * <p>
 * 경로 세그먼트를 문자열로 명시한다. AttachmentUsage.keySegment() 가 마침 posts/tasks 를
 * 주지만 그것은 <b>S3 키 접두사</b>를 정하는 값이다. 거기 얹으면 S3 키 규칙을 바꾸는
 * 순간 API 경로가 조용히 따라 바뀐다. 두 개념을 묶지 않는다.
 */
@Component
public class AttachmentDownloadUrlFactory {

    private static final String POST_PATTERN = "/api/projects/%d/posts/attachments/%d/download-url";
    private static final String TASK_PATTERN = "/api/projects/%d/tasks/attachments/%d/download-url";

    private final String baseUrl;

    public AttachmentDownloadUrlFactory(ApiProperties apiProperties) {
        String configured = apiProperties.baseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    public String forPost(Long projectId, Long postAttachmentId) {
        return baseUrl + POST_PATTERN.formatted(projectId, postAttachmentId);
    }

    public String forTask(Long projectId, Long taskAttachmentId) {
        return baseUrl + TASK_PATTERN.formatted(projectId, taskAttachmentId);
    }
}
