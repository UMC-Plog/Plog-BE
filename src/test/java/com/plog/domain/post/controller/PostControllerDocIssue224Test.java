package com.plog.domain.post.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.post.controller.docs.PostControllerDoc;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PostControllerDocIssue224Test {

    @Test
    void 공지_이력_API의_성공과_권한_오류_응답을_문서화한다() throws Exception {
        ApiResponses responses = PostControllerDoc.class
                .getMethod("getNotices", Long.class, Long.class)
                .getAnnotation(ApiResponses.class);

        assertThat(responses).isNotNull();
        assertThat(Arrays.stream(responses.value())
                .map(response -> response.responseCode())
                .toList())
                .containsExactly("200", "403", "404");
    }
}
