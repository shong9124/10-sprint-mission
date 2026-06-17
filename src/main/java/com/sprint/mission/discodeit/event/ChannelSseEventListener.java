package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.channel.ChannelDto;
import com.sprint.mission.discodeit.dto.user.UserDto;
import com.sprint.mission.discodeit.entity.ChannelType;
import com.sprint.mission.discodeit.service.basic.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelSseEventListener {

    private final SseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChannelSseEvent event) {
        ChannelDto channel = event.channel();

        if (channel.type() == ChannelType.PUBLIC) {
            sseService.broadcast(event.eventName(), channel);
            log.info("[CHANNEL_SSE_EVENT_BROADCAST] eventName={}, channelId={}",
                    event.eventName(), channel.id());
            return;
        }

        List<UUID> receiverIds = channel.participants().stream()
                .map(UserDto::id)
                .filter(Objects::nonNull)
                .toList();

        sseService.send(receiverIds, event.eventName(), channel);
        log.info("[CHANNEL_SSE_EVENT_SEND] eventName={}, channelId={}, receiverCount={}",
                event.eventName(), channel.id(), receiverIds.size());
    }
}
