package com.plog.domain.chat.dto.response;

import java.io.InputStream;

/**
 * 실제로 연 S3 객체. JSON 직렬화 대상이 아니다.
 * <p>
 * stream 은 호출자(컨트롤러)가 응답 본문으로 흘려보내며 닫는다. 이 객체를 만드는 순간
 * 커넥션이 점유되므로, 본문을 쓰지 않을 수 있는 경로(304 등)에서는 만들면 안 된다 —
 * {@link ChatAttachmentMeta} 참조.
 */
public record ChatAttachmentDownload(
        long contentLength,
        InputStream stream
) {
}
