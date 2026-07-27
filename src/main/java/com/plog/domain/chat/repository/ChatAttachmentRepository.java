package com.plog.domain.chat.repository;

import com.plog.domain.chat.entity.ChatAttachment;

import java.util.Collection;
import java.util.List;
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
}