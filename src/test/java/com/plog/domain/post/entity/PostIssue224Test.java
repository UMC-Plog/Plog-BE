package com.plog.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.plog.domain.project.entity.ProjectMember;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PostIssue224Test {

    @Test
    void 제목을_수정할_수_있다() {
        Post post = Post.builder()
                .projectMember(ProjectMember.builder().build())
                .title("기존 제목")
                .content("본문")
                .build();

        post.updateTitle("새 제목");

        assertThat(post.getTitle()).isEqualTo("새 제목");
    }

    @Test
    void 공지를_해제해도_공지_지정_시각은_보존된다() {
        Post post = Post.builder()
                .projectMember(ProjectMember.builder().build())
                .title("공지")
                .content("본문")
                .build();

        post.markAsNotice();
        LocalDateTime noticedAt = post.getNoticedAt();
        post.unpinNotice();

        assertThat(post.isNotice()).isFalse();
        assertThat(post.getNoticedAt()).isEqualTo(noticedAt);
    }
}
