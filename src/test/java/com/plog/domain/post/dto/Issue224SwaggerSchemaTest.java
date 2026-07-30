package com.plog.domain.post.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.notification.dto.FcmTokenDto;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

class Issue224SwaggerSchemaTest {

    @Test
    void 댓글과_FCM_응답은_서로_다른_스키마명을_사용한다() {
        assertThat(CommentDto.Response.class.getAnnotation(Schema.class).name())
                .isEqualTo("PostCommentResponse");
        assertThat(FcmTokenDto.Response.class.getAnnotation(Schema.class).name())
                .isEqualTo("FcmTokenResponse");
        assertThat(PostDto.UpdateRequest.class.getAnnotation(Schema.class).name())
                .isEqualTo("PostUpdateRequest");
        assertThat(PostDto.UpdateResponse.class.getAnnotation(Schema.class).name())
                .isEqualTo("PostUpdateResponse");
    }
}
