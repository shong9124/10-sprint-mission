package com.sprint.mission.discodeit.repository;

import com.sprint.mission.discodeit.dto.sse.SseMessage;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
public class SseMessageRepository {

    private static final int MAX_MESSAGE_SIZE = 1000;

    private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
    private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();

    public SseMessage save(Collection<UUID> receiverIds, String eventName, Object data) {
        return save(receiverIds, eventName, data, false);
    }

    public SseMessage saveBroadcast(String eventName, Object data) {
        return save(Set.of(), eventName, data, true);
    }

    public List<SseMessage> findAllAfter(UUID lastEventId, UUID receiverId) {
        if (lastEventId == null || !messages.containsKey(lastEventId)) {
            return List.of();
        }

        List<SseMessage> result = new ArrayList<>();
        boolean collect = false;

        for (UUID eventId : eventIdQueue) {
            if (collect) {
                SseMessage message = messages.get(eventId);
                if (message != null && message.isReceivableBy(receiverId)) {
                    result.add(message);
                }
                continue;
            }

            if (eventId.equals(lastEventId)) {
                collect = true;
            }
        }

        return result;
    }

    private SseMessage save(
            Collection<UUID> receiverIds,
            String eventName,
            Object data,
            boolean broadcast
    ) {
        UUID eventId = UUID.randomUUID();
        SseMessage message = new SseMessage(
                eventId,
                Set.copyOf(new LinkedHashSet<>(receiverIds)),
                eventName,
                data,
                broadcast
        );

        messages.put(eventId, message);
        eventIdQueue.add(eventId);
        trim();

        return message;
    }

    private void trim() {
        while (eventIdQueue.size() > MAX_MESSAGE_SIZE) {
            UUID eventId = eventIdQueue.poll();
            if (eventId != null) {
                messages.remove(eventId);
            }
        }
    }
}
