package com.plog.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.plog.domain.chat.dto.response.ChatAttachmentDownload;
import com.plog.domain.chat.dto.response.ChatAttachmentMeta;
import com.plog.domain.chat.entity.ChatAttachment;
import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.entity.ChatRoom;
import com.plog.domain.chat.repository.ChatAttachmentRepository;
import com.plog.domain.chat.repository.ChatRoomRepository;
import com.plog.domain.project.entity.MemberStatus;
import com.plog.global.api.error.ChatErrorCode;
import com.plog.global.api.exception.ApiException;
import com.plog.infrastructure.s3.FileStorageService;
import com.plog.infrastructure.s3.UploadedFile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class ChatAttachmentDownloadServiceTest {

    private final ChatAttachmentRepository chatAttachmentRepository =
            mock(ChatAttachmentRepository.class);
    private final ChatRoomRepository chatRoomRepository = mock(ChatRoomRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final ChatAttachmentDownloadService service = new ChatAttachmentDownloadService(
            chatAttachmentRepository, chatRoomRepository, fileStorageService);

    @Test
    void 첨부가_없으면_CHAT010() {
        given(chatAttachmentRepository.findWithFileAndRoomById(3L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_ATTACHMENT_NOT_FOUND);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void 방_멤버가_아니면_CHAT002() {
        // mock 생성·스텁을 given(...) 인자 안에서 하면 바깥 스터빙이 끝나기 전에 실행돼
        // UnfinishedStubbingException 이 난다. 반드시 밖에서 만들어 둔다.
        ChatAttachment attachment = attachment();
        given(chatAttachmentRepository.findWithFileAndRoomById(3L))
                .willReturn(Optional.of(attachment));
        given(chatRoomRepository.findAccessibleRoom(anyLong(), anyLong(), any(MemberStatus.class)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(3L, 7L))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void resolve는_S3를_건드리지_않고_메타만_돌려준다() {
        ChatAttachment attachment = attachment();
        ChatRoom accessibleRoom = mock(ChatRoom.class);
        given(chatAttachmentRepository.findWithFileAndRoomById(3L))
                .willReturn(Optional.of(attachment));
        given(chatRoomRepository.findAccessibleRoom(anyLong(), anyLong(), any(MemberStatus.class)))
                .willReturn(Optional.of(accessibleRoom));

        ChatAttachmentMeta meta = service.resolve(3L, 7L);

        assertThat(meta.fileKey()).isEqualTo("chats/users/1/uuid/a.png");
        assertThat(meta.contentType()).isEqualTo("image/png");
        assertThat(meta.originalFilename()).isEqualTo("photo.png");
        assertThat(meta.eTag()).isEqualTo("\"55\"");
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void S3에_객체가_없으면_CHAT010() {
        given(fileStorageService.openStream(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(meta()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_ATTACHMENT_NOT_FOUND);
    }

    @Test
    void open은_S3_응답의_길이와_스트림을_담아_돌려준다() {
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> stream = mock(ResponseInputStream.class);
        given(stream.response())
                .willReturn(GetObjectResponse.builder().contentLength(1234L).build());
        given(fileStorageService.openStream("chats/users/1/uuid/a.png"))
                .willReturn(Optional.of(stream));

        ChatAttachmentDownload download = service.open(meta());

        assertThat(download.contentLength()).isEqualTo(1234L);
        assertThat(download.stream()).isSameAs(stream);
    }

    private ChatAttachmentMeta meta() {
        return new ChatAttachmentMeta(
                "chats/users/1/uuid/a.png", "image/png", "photo.png", "\"55\"");
    }

    /** 프록시가 읽는 값만 스텁한다: 파일 키, Content-Type, 파일명, uploadedFile.id, 방 ID. */
    private ChatAttachment attachment() {
        UploadedFile file = mock(UploadedFile.class);
        given(file.getFileKey()).willReturn("chats/users/1/uuid/a.png");
        given(file.getContentType()).willReturn("image/png");
        given(file.getOriginalFilename()).willReturn("photo.png");
        given(file.getId()).willReturn(55L);

        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(11L);

        ChatMessage message = mock(ChatMessage.class);
        given(message.getChatRoom()).willReturn(room);

        ChatAttachment attachment = mock(ChatAttachment.class);
        given(attachment.getUploadedFile()).willReturn(file);
        given(attachment.getChatMessage()).willReturn(message);
        return attachment;
    }
}
