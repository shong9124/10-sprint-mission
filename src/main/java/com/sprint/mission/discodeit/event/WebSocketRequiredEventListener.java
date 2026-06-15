package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.message.MessageDto;
import com.sprint.mission.discodeit.entity.Message;
import com.sprint.mission.discodeit.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WebSocketRequiredEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageMapper messageMapper;

    // 메시지 저장 트랜잭션이 정상적으로 커밋된 이후에만 구독자에게 생성 결과를 전송
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMessage(MessageCreatedEvent event) {
        Message message = event.message();
        MessageDto payload = messageMapper.toDto(message);

        // 각 채널의 구독자만 메시지를 받도록 채널 ID를 STOMP 목적지에 포함
        String destination = "/sub/channels.%s.messages"
                .formatted(message.getChannel().getId());

        messagingTemplate.convertAndSend(destination, payload);
    }
}
