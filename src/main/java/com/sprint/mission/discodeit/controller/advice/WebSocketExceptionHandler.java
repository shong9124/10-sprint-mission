package com.sprint.mission.discodeit.controller.advice;

import com.sprint.mission.discodeit.controller.MessageWebSocketController;
import com.sprint.mission.discodeit.controller.dto.ErrorResponseDTO;
import com.sprint.mission.discodeit.exception.DiscodeitException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice(assignableTypes = MessageWebSocketController.class)
public class WebSocketExceptionHandler {

    // 예외를 발생시킨 WebSocket 세션의 /user/sub/errors 구독으로만 오류를 전송한다.
    @MessageExceptionHandler(DiscodeitException.class)
    @SendToUser(value = "/sub/errors", broadcast = false)
    public ErrorResponseDTO handleDiscodeitException(DiscodeitException exception) {
        return new ErrorResponseDTO(
                exception.getTimestamp(),
                exception.getErrorCode().getStatus(),
                exception.getErrorCode().getCode(),
                exception.getErrorCode().getMessage(),
                exception.getDetails(),
                exception.getClass().getSimpleName()
        );
    }
}
