package com.sprint.mission.discodeit.dto.sse;

import java.util.Set;
import java.util.UUID;

public record SseMessage(
        UUID id,
        Set<UUID> receiverIds,
        String eventName,
        Object data,
        boolean broadcast
) {

    public boolean isReceivableBy(UUID receiverId) {
        return broadcast || receiverIds.contains(receiverId);
    }
}
