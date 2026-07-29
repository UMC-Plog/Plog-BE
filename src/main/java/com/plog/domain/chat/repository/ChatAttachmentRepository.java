package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatAttachment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    /*
     * uploadedFile 을 반드시 fetch join 한다. 브로드캐스트 리스너는 @Async +
     * AFTER_COMMIT 이라 트랜잭션 밖의 다른 스레드에서 돌고(OSIV 도 꺼져 있다),
     * 지연 프록시를 건드리는 순간 LazyInitializationException 으로 조용히 죽는다.
     */
    @Query("select a from ChatAttachment a join fetch a.uploadedFile "
            + "where a.chatMessage.id = :chatMessageId order by a.id asc")
    List<ChatAttachment> findAllByChatMessageIdOrderByIdAsc(
            @Param("chatMessageId") Long chatMessageId);

    // 메시지 목록 조회 시 N+1 방지 — 여러 메시지의 첨부파일을 한 번에 조회
    @Query("select a from ChatAttachment a join fetch a.uploadedFile "
            + "where a.chatMessage.id in :chatMessageIds order by a.id asc")
    List<ChatAttachment> findAllByChatMessageIdInOrderByIdAsc(
            @Param("chatMessageIds") Collection<Long> chatMessageIds);

    /*
     * 프록시 전달용 단건 조회. uploadedFile(파일 키·Content-Type)과 chatRoom(권한 검사용
     * 방 ID)을 함께 쓰므로 둘 다 fetch join 한다. 지연 로딩으로 두면 요청마다 쿼리가
     * 세 번 나간다.
     */
    @Query("select a from ChatAttachment a "
            + "join fetch a.uploadedFile "
            + "join fetch a.chatMessage m "
            + "join fetch m.chatRoom "
            + "where a.id = :chatAttachmentId")
    Optional<ChatAttachment> findWithFileAndRoomById(
            @Param("chatAttachmentId") Long chatAttachmentId);

    /*
     * 썸네일 준비 push 용. 방 ID(목적지)와 uploadedFile(URL 판정)이 둘 다 필요하고,
     * 리스너는 트랜잭션 밖의 스케줄러 스레드에서 도는 데다 OSIV 도 꺼져 있어
     * 지연 로딩을 건드리면 LazyInitializationException 으로 조용히 죽는다.
     *
     * chat_attachments 의 uk_chat_attachment_file 이 file_id 를 UNIQUE 로 잡아
     * 첨부:파일이 1:1이므로 단건 조회가 성립한다.
     */
    @Query("select a from ChatAttachment a "
            + "join fetch a.uploadedFile f "
            + "join fetch a.chatMessage m "
            + "join fetch m.chatRoom "
            + "where f.id = :uploadedFileId")
    Optional<ChatAttachment> findWithFileAndRoomByUploadedFileId(
            @Param("uploadedFileId") Long uploadedFileId);
}