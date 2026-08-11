package com.plog.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.plog.domain.project.entity.ProjectMember;
import com.plog.domain.post.entity.Comment;
import com.plog.domain.post.entity.Post;
import com.plog.domain.post.repository.CommentRepository;
import com.plog.domain.post.repository.PostRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostActivityLogServiceTest {
    @Mock private ReportActivityLogRepository activityLogRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;

    private PostActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new PostActivityLogService(activityLogRepository, postRepository, commentRepository);
    }

    @Test
    void 게시글_생성_원문을_활동_로그로_보존한다() {
        ProjectMember member = ProjectMember.builder().id(7L).build();
        Post post = Post.builder().id(11L).projectMember(member).content("첫 게시글").build();
        when(postRepository.findById(11L)).thenReturn(java.util.Optional.of(post));
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.POST, "post:11"))
                .thenReturn(false);

        service.collectPostCreated(11L, 7L, "첫 게시글", LocalDateTime.of(2026, 8, 4, 10, 0));

        verify(activityLogRepository).acquireSourceLock("POST:post:11");
        ArgumentCaptor<ReportActivityLog> captor = ArgumentCaptor.forClass(ReportActivityLog.class);
        verify(activityLogRepository).save(captor.capture());
        ReportActivityLog saved = captor.getValue();
        assertThat(saved.getSourceDomain()).isEqualTo(SourceDomain.POST);
        assertThat(saved.getRawActivityType()).isEqualTo(RawActivityType.POST_CREATE);
        assertThat(saved.getSourceRefId()).isEqualTo("post:11");
        assertThat(saved.getContent()).isEqualTo("첫 게시글");
        assertThat(saved.getMetadata()).isEqualTo("{\"postId\":11}");
    }

    @Test
    void 같은_원본_이벤트는_중복_저장하지_않는다() {
        ProjectMember member = ProjectMember.builder().id(7L).build();
        Post post = Post.builder().id(11L).build();
        Comment comment = Comment.builder().id(31L).post(post).projectMember(member).content("댓글").build();
        when(commentRepository.findById(31L)).thenReturn(java.util.Optional.of(comment));
        when(activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.POST, "comment:31"))
                .thenReturn(true);

        service.collectCommentCreated(31L, 11L, 7L, "댓글", LocalDateTime.now());

        verify(activityLogRepository).acquireSourceLock("POST:comment:31");
        verify(activityLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 삭제된_게시글의_늦은_비동기_이벤트는_활동로그를_만들지_않는다() {
        when(postRepository.findById(11L)).thenReturn(java.util.Optional.empty());

        service.collectPostCreated(11L, 7L, "삭제된 글", LocalDateTime.now());

        verify(activityLogRepository)
                .deleteBySourceDomainAndSourceRefId(SourceDomain.POST, "post:11");
        verify(activityLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
