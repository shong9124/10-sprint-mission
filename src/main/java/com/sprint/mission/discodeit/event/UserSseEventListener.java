package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.service.basic.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSseEventListener {

    private final SseService sseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(UserSseEvent event) {
        sseService.broadcast(event.eventName(), event.user());
        log.info("[USER_SSE_EVENT_BROADCAST] eventName={}, userId={}",
                event.eventName(), event.user().id());
    }
}
