package com.plog.domain.chat.controller;

import com.plog.domain.chat.controller.docs.ChatAttachmentControllerDoc;
import com.plog.domain.chat.dto.response.ChatAttachmentDownload;
import com.plog.domain.chat.dto.response.ChatAttachmentMeta;
import com.plog.domain.chat.service.ChatAttachmentDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.util.UriUtils;

/**
 * 채팅 첨부 프록시. 경로를 채팅방 하위가 아니라 최상위에 두는 이유는 plog_media 쿠키를
 * Path=/api/chat-attachments 로 이 엔드포인트에만 스코프하기 위해서다. 방 하위에 두면
 * 쿠키가 메시지 조회·읽음처리 요청에도 딸려간다.
 * <p>
 * 응답은 ApiResponse 로 감싸지 않는다 — &lt;img&gt; 가 읽는 것은 바이트 그 자체다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-attachments")
public class ChatAttachmentController implements ChatAttachmentControllerDoc {

    private static final String CACHE_CONTROL = "private, max-age=31536000, immutable";

    private final ChatAttachmentDownloadService chatAttachmentDownloadService;

    @Override
    @GetMapping("/{chatAttachmentId}")
    public ResponseEntity<Resource> download(
            @PathVariable Long chatAttachmentId,
            @AuthenticationPrincipal Long userId,
            WebRequest webRequest,
            HttpServletResponse response
    ) {
        ChatAttachmentMeta meta = chatAttachmentDownloadService.resolve(chatAttachmentId, userId);

        // 304 판정을 S3 를 열기 '전에' 한다. 순서를 뒤집으면 Spring 이 본문 쓰기를 건너뛰는
        // 단축 경로에서 아무도 스트림을 닫지 않아 S3 커넥션이 샌다(ChatAttachmentMeta 참조).
        if (webRequest.checkNotModified(meta.eTag())) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
            return null;
        }

        ChatAttachmentDownload download = chatAttachmentDownloadService.open(meta);
        // InputStreamResource 는 스트림을 메모리에 올리지 않고 흘려보내며, 쓰기가 끝나면
        // Spring 이 닫는다. byte[] 로 읽으면 10MB 이미지 동시 20건에 200MB 가 힙에 뜬다.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .header(HttpHeaders.ETAG, meta.eTag())
                // 이미지는 인라인으로 두되 저장 시 파일명을 살린다. 프록시 URL 의 마지막
                // 세그먼트가 숫자 id 라, 없으면 pdf·docx·zip 이 "3" 으로 저장된다.
                .header(HttpHeaders.CONTENT_DISPOSITION, inlineDisposition(meta.originalFilename()))
                // 사용자 업로드물을 API 오리진에서 서빙하므로 MIME 스니핑을 막는다.
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.stream()));
    }

    private String inlineDisposition(String fileName) {
        return "inline; filename*=UTF-8''" + UriUtils.encode(fileName, StandardCharsets.UTF_8);
    }
}
