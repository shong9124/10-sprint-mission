package com.sprint.mission.discodeit.service.basic;

import com.sprint.mission.discodeit.dto.sse.SseMessage;
import com.sprint.mission.discodeit.repository.SseEmitterRepository;
import com.sprint.mission.discodeit.repository.SseMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

    private static final long SSE_TIMEOUT_MILLIS = 1000L * 60 * 30;
    private static final String PING_EVENT_NAME = "ping";

    private final SseEmitterRepository sseEmitterRepository;
    private final SseMessageRepository sseMessageRepository;

    public SseEmitter connect(UUID receiverId, UUID lastEventId) {
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        sseEmitter.onCompletion(() -> sseEmitterRepository.delete(receiverId, sseEmitter));
        sseEmitter.onTimeout(() -> sseEmitterRepository.delete(receiverId, sseEmitter));
        sseEmitter.onError(error -> sseEmitterRepository.delete(receiverId, sseEmitter));

        sseEmitterRepository.save(receiverId, sseEmitter);

        if (!ping(sseEmitter)) {
            sseEmitterRepository.delete(receiverId, sseEmitter);
            return sseEmitter;
        }

        replayMissedMessages(receiverId, lastEventId, sseEmitter);

        return sseEmitter;
    }

    public void send(Collection<UUID> receiverIds, String eventName, Object data) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            return;
        }

        Set<UUID> distinctReceiverIds = Set.copyOf(receiverIds);
        SseMessage message = sseMessageRepository.save(distinctReceiverIds, eventName, data);

        for (UUID receiverId : distinctReceiverIds) {
            sendToReceiver(receiverId, message);
        }
    }

    public void broadcast(String eventName, Object data) {
        SseMessage message = sseMessageRepository.saveBroadcast(eventName, data);

        for (UUID receiverId : sseEmitterRepository.findAllReceiverIds()) {
            sendToReceiver(receiverId, message);
        }
    }

    @Scheduled(fixedDelay = 1000 * 60 * 30)
    public void cleanUp() {
        for (Map.Entry<UUID, List<SseEmitter>> entry : sseEmitterRepository.findAll().entrySet()) {
            UUID receiverId = entry.getKey();

            for (SseEmitter sseEmitter : entry.getValue()) {
                if (!ping(sseEmitter)) {
                    sseEmitterRepository.delete(receiverId, sseEmitter);
                }
            }
        }
    }

    private void replayMissedMessages(UUID receiverId, UUID lastEventId, SseEmitter sseEmitter) {
        for (SseMessage message : sseMessageRepository.findAllAfter(lastEventId, receiverId)) {
            if (!send(sseEmitter, message)) {
                sseEmitterRepository.delete(receiverId, sseEmitter);
                return;
            }
        }
    }

    private void sendToReceiver(UUID receiverId, SseMessage message) {
        for (SseEmitter sseEmitter : sseEmitterRepository.findAllByReceiverId(receiverId)) {
            if (!send(sseEmitter, message)) {
                sseEmitterRepository.delete(receiverId, sseEmitter);
            }
        }
    }

    private boolean send(SseEmitter sseEmitter, SseMessage message) {
        try {
            sseEmitter.send(SseEmitter.event()
                    .id(message.id().toString())
                    .name(message.eventName())
                    .data(message.data()));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("[SSE_SEND_FAIL] eventId={}, eventName={}", message.id(), message.eventName(), e);
            return false;
        }
    }

    private boolean ping(SseEmitter sseEmitter) {
        try {
            sseEmitter.send(SseEmitter.event()
                    .name(PING_EVENT_NAME)
                    .data("ping"));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("[SSE_PING_FAIL]", e);
            return false;
        }
    }
}
