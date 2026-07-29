package com.plog.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.plog.global.api.exception.ApiException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UploadedFileServiceTest {

    private static final String FILE_KEY = "chats/users/7/uuid/a.png";

    @Mock private UploadedFileRepository repository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private UploadedFileService service;

    /**
     * ThumbnailProperties 는 record 라 @InjectMocks 로는 못 채운다. 썸네일 판정 자체는
     * UploadedFileServiceThumbnailTest 가 다루므로 여기서는 꺼 둔다.
     */
    @BeforeEach
    void setUp() {
        service = new UploadedFileService(repository, fileStorageService, eventPublisher,
                new ThumbnailProperties(false, "plog-thumbnail", 640));
    }

    private UploadedFile pendingFile() {
        return UploadedFile.issue(FILE_KEY, 7L, AttachmentUsage.CHAT,
                "a.png", "image/png", 100L, LocalDateTime.of(2026, 7, 27, 10, 0));
    }

    @Test
    void 이미_확정된_키는_거부한다() {
        given(repository.findByFileKey(anyString())).willReturn(Optional.of(pendingFile()));
        given(fileStorageService.headMatches(anyString(), anyLong(), anyString())).willReturn(true);
        given(repository.confirmIfPending(anyString(), any(), any(), any())).willReturn(0);

        assertThatThrownBy(() -> service.confirmNew(AttachmentUsage.CHAT, 7L,
                FILE_KEY, "a.png", 100L, FileStorageErrorCode.INVALID_FILE_KEY))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.FILE_ALREADY_ATTACHED));
    }

    @Test
    void 다른_사용자의_키는_거부한다() {
        given(repository.findByFileKey(anyString())).willReturn(Optional.of(pendingFile()));

        assertThatThrownBy(() -> service.confirmNew(AttachmentUsage.CHAT, 99L,
                FILE_KEY, "a.png", 100L, FileStorageErrorCode.INVALID_FILE_KEY))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(FileStorageErrorCode.FILE_NOT_OWNED));
    }

    @Test
    void 용도가_다르면_거부한다() {
        given(repository.findByFileKey(anyString())).willReturn(Optional.of(pendingFile()));

        assertThatThrownBy(() -> service.confirmNew(AttachmentUsage.POST, 7L,
                FILE_KEY, "a.png", 100L, FileStorageErrorCode.INVALID_FILE_KEY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void 정상_확정은_레지스트리_행을_돌려준다() {
        UploadedFile file = pendingFile();
        given(repository.findByFileKey(anyString())).willReturn(Optional.of(file));
        given(fileStorageService.headMatches(anyString(), anyLong(), anyString())).willReturn(true);
        given(repository.confirmIfPending(anyString(), any(), any(), any())).willReturn(1);

        UploadedFile confirmed = service.confirmNew(AttachmentUsage.CHAT, 7L,
                FILE_KEY, "a.png", 100L, FileStorageErrorCode.INVALID_FILE_KEY);

        assertThat(confirmed).isSameAs(file);
        // 조건부 UPDATE 는 DB만 바꾼다. 같은 트랜잭션의 이 인스턴스까지 맞춰주지 않으면
        // 나중에 더티 체킹이 status=PENDING, confirmed_at=null 을 되써버린다.
        assertThat(confirmed.getStatus()).isEqualTo(UploadedFileStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getTaggedAt()).isNull();
    }

    @Test
    void 이_리소스가_참조하지_않는_fileId는_거부한다() {
        assertThatThrownBy(() -> service.requireOwnedByResource(
                42L, Set.of(1L, 2L), FileStorageErrorCode.INVALID_FILE_KEY))
                .isInstanceOf(ApiException.class);
    }
}
