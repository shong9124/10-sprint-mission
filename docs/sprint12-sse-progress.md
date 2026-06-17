# Sprint 12 SSE Progress

## 구현 상태

SSE 연결 엔드포인트는 `/api/sse`로 구현되어 있으며, 인증된 사용자의 ID 기준으로 `SseEmitter`를 등록한다.

구현된 이벤트:

| 이벤트 | Payload | 전송 범위 | 상태 |
| --- | --- | --- | --- |
| `notifications.created` | `NotificationDto` | 알림 수신자 | 정상 동작 확인 |
| `binaryContents.updated` | `BinaryContentDto` | broadcast | SUCCESS/FAIL 변경 및 이벤트 확인 |
| `channels.created` | `ChannelDto` | 공개 채널 broadcast, 비공개 채널 참여자 | 정상 동작 확인 |
| `channels.updated` | `ChannelDto` | 공개 채널 broadcast, 비공개 채널 참여자 | 정상 동작 확인 |
| `channels.deleted` | `ChannelDto` | 공개 채널 broadcast, 비공개 채널 참여자 | 정상 동작 확인 |
| `users.created` | `UserDto` | broadcast | DB 기반 이벤트 구현 |
| `users.updated` | `UserDto` | broadcast | DB 변경 및 로그인/로그아웃 online 상태 확인 |
| `users.deleted` | `UserDto` | broadcast | DB 기반 이벤트 구현 |

## SSE 인프라

### 연결 관리

- `SseController`가 `/api/sse` 요청을 받아 `SseService.connect(...)`로 위임한다.
- `SseEmitterRepository`는 사용자 ID별로 여러 `SseEmitter`를 저장한다.
- 다중 탭/다중 브라우저 연결을 위해 사용자별 `List<SseEmitter>` 구조를 사용한다.
- emitter 완료, 타임아웃, 에러 발생 시 repository에서 제거한다.
- 이벤트 전송 또는 ping 실패 시에도 해당 emitter를 제거한다.

### Last-Event-ID 재전송

- `SseMessageRepository`는 최근 SSE 메시지를 메모리에 저장한다.
- `SseService.connect(...)`는 `Last-Event-ID`가 있으면 저장된 이후 이벤트를 재전송한다.
- broadcast 이벤트와 사용자 지정 이벤트 모두 수신 가능 여부를 검사해 재전송한다.
- 저장소는 최대 1000개 메시지만 보관한다.
- 서버 재시작 또는 trim으로 `lastEventId`가 사라진 경우 재전송하지 않는다.

### cleanup / ping

- `@EnableScheduling`이 활성화되어 있다.
- `SseService.cleanUp()`은 30분 간격으로 전체 emitter에 `ping` 이벤트를 보낸다.
- ping 실패 emitter는 repository에서 제거한다.

## 트랜잭션 처리

DB 변경 기반 이벤트는 커밋 이후에만 SSE가 발송되도록 처리되어 있다.

- `channels.*`: `ChannelSseEventListener`의 `@TransactionalEventListener(AFTER_COMMIT)`에서 전송
- `users.created/updated/deleted`: `UserSseEventListener`의 `@TransactionalEventListener(AFTER_COMMIT)`에서 전송
- `binaryContents.updated`: `TransactionSynchronization.afterCommit()`에서 전송
- `notifications.created`: Kafka listener 내부에서 notification 저장 후 `TransactionSynchronization.afterCommit()`으로 전송

로그인/로그아웃 online 상태 변경은 DB 트랜잭션이 아닌 Security handler 흐름이다.

- 로그인: JWT 등록 이후 `online=true` `UserDto`로 `users.updated` broadcast
- 로그아웃: JWT 무효화 이후 `online=false` `UserDto`로 `users.updated` broadcast
- refresh token 회전과 JWT cleanup 만료 처리는 아직 SSE 이벤트 대상에 포함하지 않았다.

## 확인된 테스트 결과

확인 완료:

- `/api/sse` 연결 성공
- `notifications.created` 정상 수신
- `binaryContents.updated` 정상 수신
- 파일 업로드 성공 시 `BinaryContent.status`가 `SUCCESS`로 변경됨
- 파일 업로드 실패 시 `BinaryContent.status`가 `FAIL`로 변경됨
- `channels.created/updated/deleted` 정상 수신
- 공개 채널 이벤트 broadcast 확인
- 비공개 채널 이벤트 참여자 대상 전송 확인
- `users.created/updated/deleted` DB 기반 이벤트 구현 확인
- 로그인 시 `users.updated`의 `online=true` 실시간 반영 확인
- 로그아웃 시 `users.updated`의 `online=false` 실시간 반영 확인
- 프로필 이미지 수정 시 `BinaryContentCreatedEvent`에 저장된 profile 객체가 실리도록 수정
- 사용자 삭제 전 `ReadStatus`, `Notification` 선삭제로 FK 오류 대응

컴파일:

- `./gradlew compileJava` 성공

## 주의 및 남은 확인 항목

- `SseMessageRepository`는 메모리 기반이므로 서버 재시작 후 이벤트 복구는 불가능하다.
- 저장 메시지는 최대 1000개로 제한된다.
- `Last-Event-ID`가 저장소에 없으면 재전송하지 않는다.
- 프로필 이미지 수정 시 `users.updated`는 사용자 정보 변경 이벤트이고, 업로드 완료 후 `binaryContents.updated`가 별도로 발송될 수 있다.
- 권한 변경 시 `users.updated`와 권한 변경 알림에 따른 `notifications.created`가 모두 발생할 수 있다.
- 현재 JWT registry는 사용자별 active token을 1개로 관리하므로, 로그아웃 이벤트는 해당 사용자를 offline으로 broadcast한다.
- refresh token 회전과 scheduled JWT cleanup으로 인한 online 상태 변화는 아직 SSE 이벤트를 발송하지 않는다.
