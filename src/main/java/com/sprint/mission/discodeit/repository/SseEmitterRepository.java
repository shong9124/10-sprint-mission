package com.sprint.mission.discodeit.repository;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class SseEmitterRepository {

    private final ConcurrentMap<UUID, List<SseEmitter>> data = new ConcurrentHashMap<>();

    public void save(UUID receiverId, SseEmitter sseEmitter) {
        data.computeIfAbsent(receiverId, ignored -> new CopyOnWriteArrayList<>())
                .add(sseEmitter);
    }

    public List<SseEmitter> findAllByReceiverId(UUID receiverId) {
        return List.copyOf(data.getOrDefault(receiverId, List.of()));
    }

    public Map<UUID, List<SseEmitter>> findAll() {
        Map<UUID, List<SseEmitter>> result = new ConcurrentHashMap<>();
        data.forEach((receiverId, emitters) -> result.put(receiverId, List.copyOf(emitters)));
        return result;
    }

    public Collection<UUID> findAllReceiverIds() {
        return new ArrayList<>(data.keySet());
    }

    public void delete(UUID receiverId, SseEmitter sseEmitter) {
        data.computeIfPresent(receiverId, (ignored, emitters) -> {
            emitters.remove(sseEmitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
