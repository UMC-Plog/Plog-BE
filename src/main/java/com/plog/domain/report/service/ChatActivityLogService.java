package com.plog.domain.report.service;

import com.plog.domain.chat.entity.ChatMessage;
import com.plog.domain.chat.repository.ChatMessageRepository;
import com.plog.domain.report.entity.RawActivityType;
import com.plog.domain.report.entity.ReportActivityLog;
import com.plog.domain.report.entity.SourceDomain;
import com.plog.domain.report.repository.ReportActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Chat 도메인 0단계 수집. 채팅 원문은 원본 엔티티에만 두고 활동 로그에는 참조 정보만 저장한다. */
@Service
@RequiredArgsConstructor
public class ChatActivityLogService {

    private final ReportActivityLogRepository activityLogRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void collectMessageSaved(Long chatMessageId) {
        String sourceRefId = "chat:" + chatMessageId;
        activityLogRepository.acquireSourceLock(SourceDomain.CHAT.name() + ":" + sourceRefId);
        if (activityLogRepository.existsBySourceDomainAndSourceRefId(SourceDomain.CHAT, sourceRefId)) {
            return;
        }

        ChatMessage message = chatMessageRepository.findWithRoomAndSenderById(chatMessageId)
                .orElseThrow(() -> new IllegalStateException(
                        "활동 로그의 채팅 메시지를 찾을 수 없습니다. chatMessageId=" + chatMessageId));
        activityLogRepository.save(ReportActivityLog.create(
                message.getProjectMember(),
                SourceDomain.CHAT,
                RawActivityType.CHAT_MESSAGE,
                null,
                message.getCreatedAt(),
                "{\"chatMessageId\":" + chatMessageId + "}",
                sourceRefId
        ));
    }
}
