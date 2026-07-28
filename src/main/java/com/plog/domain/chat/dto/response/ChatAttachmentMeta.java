package com.plog.domain.chat.dto.response;

/**
 * 첨부 조회·권한 검사만으로 얻는 메타데이터. <b>S3를 열지 않는다.</b>
 * <p>
 * 스트림 열기와 분리한 이유는 조건부 요청(If-None-Match) 때문이다. Spring MVC 의
 * HttpEntityMethodProcessor 는 status 200 + GET + ETag 일치면 메시지 컨버터를 건너뛰고
 * 바로 flush 한다. 그 경로에서는 ResourceHttpMessageConverter 가 InputStreamResource 를
 * 닫아 주지 않아, 스트림을 미리 열어 두면 304 마다 S3 커넥션이 샌다.
 * 메타만으로 304를 판정하고 나서 열면 그 경로 자체가 사라진다.
 */
public record ChatAttachmentMeta(
        String fileKey,
        String contentType,
        String originalFilename,
        String eTag
) {
}
