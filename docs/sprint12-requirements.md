# Sprint 12 요구사항

## 목표

기존 클라이언트 폴링 방식을 Server-Sent Events(SSE) 기반 실시간 이벤트 전달 방식으로 리팩토링한다.

## 작업 범위

- [ ] SSE 환경을 구성한다.
- [ ] 클라이언트에서 SSE 연결을 위한 엔드포인트를 구현한다.
- [ ] 사용자별 `SseEmitter` 객체를 생성하고 메시지를 전송하는 컴포넌트를 구현한다.
- [ ] `SseEmitter` 객체를 메모리에 저장하는 컴포넌트를 구현한다.
- [ ] 이벤트 유실 복원을 위해 SSE 메시지를 저장하는 컴포넌트를 구현한다.
- [ ] 기존에 클라이언트에서 폴링 방식으로 주기적으로 요청하던 데이터를 SSE를 이용해 서버에서 실시간으로 전달하는 방식으로 리팩토링한다.

## SSE 연결 API

### `GET /api/sse`

클라이언트가 SSE 연결을 생성하기 위한 엔드포인트를 구현한다.

요구사항:

- 인증된 사용자 기준으로 SSE 연결을 생성한다.
- 사용자별로 `SseEmitter`를 생성하고 저장한다.
- 클라이언트가 `Last-Event-ID` 값을 전달하면 해당 이벤트 이후 유실된 메시지를 복원해서 전송한다.
- 최초 연결 확인을 위해 더미 이벤트를 전송할 수 있어야 한다.

## SSE 서비스

사용자별 `SseEmitter` 객체 생성, 이벤트 전송, 브로드캐스트, 만료 연결 정리를 담당하는 서비스를 구현한다.

```java
@Service
public class SseService {

  public SseEmitter connect(UUID receiverId, UUID lastEventId) { ... }

  public void send(Collection<UUID> receiverIds, String eventName, Object data) { ... }

  public void broadcast(String eventName, Object data) { ... }

  @Scheduled(fixedDelay = 1000 * 60 * 30)
  public void cleanUp() { ... }

  private boolean ping(SseEmitter sseEmitter) { ... }
}
```

### 메서드 요구사항

#### `connect(UUID receiverId, UUID lastEventId)`

- `receiverId` 사용자에 대한 `SseEmitter` 객체를 생성한다.
- 생성한 `SseEmitter`를 저장소에 등록한다.
- 최초 연결 확인을 위해 `ping` 더미 이벤트를 전송한다.
- `lastEventId`가 존재하면 해당 이벤트 이후 저장된 메시지를 조회해 재전송한다.
- 연결 완료, 타임아웃, 오류 발생 시 저장소에서 해당 `SseEmitter`를 제거한다.

#### `send(Collection<UUID> receiverIds, String eventName, Object data)`

- 수신자 목록에 포함된 사용자들의 모든 `SseEmitter` 연결로 이벤트를 전송한다.
- 메시지마다 고유한 이벤트 ID를 부여한다.
- 이벤트 유실 복원을 위해 전송 메시지를 저장한다.
- 전송에 실패한 `SseEmitter`는 저장소에서 제거한다.

#### `broadcast(String eventName, Object data)`

- 현재 연결된 모든 사용자에게 이벤트를 전송한다.
- 메시지마다 고유한 이벤트 ID를 부여한다.
- 이벤트 유실 복원을 위해 전송 메시지를 저장한다.
- 전송에 실패한 `SseEmitter`는 저장소에서 제거한다.

#### `cleanUp()`

- 30분마다 실행한다.
- 저장된 모든 `SseEmitter`에 `ping` 더미 이벤트를 보낸다.
- `ping` 전송에 실패한 만료 연결은 저장소에서 제거한다.

#### `ping(SseEmitter sseEmitter)`

- 최초 연결 또는 만료 여부 확인 용도의 더미 이벤트를 보낸다.
- 전송 성공 여부를 `boolean`으로 반환한다.

## SseEmitter 저장소

사용자별 SSE 연결을 메모리에 저장하는 컴포넌트를 구현한다.

```java
@Repository
public class SseEmitterRepository {

  private final ConcurrentMap<UUID, List<SseEmitter>> data = new ConcurrentHashMap<>();

  // ...
}
```

요구사항:

- `ConcurrentMap` 등 스레드 세이프한 자료구조를 사용한다.
- 사용자 한 명이 여러 개의 연결을 가질 수 있어야 한다.
- 다중 탭 또는 다중 브라우저 세션을 고려해 `List<SseEmitter>` 구조를 사용한다.
- 특정 사용자에게 연결된 모든 `SseEmitter`를 조회할 수 있어야 한다.
- 전체 사용자에게 연결된 모든 `SseEmitter`를 조회할 수 있어야 한다.
- 특정 사용자의 특정 `SseEmitter`를 제거할 수 있어야 한다.
- 더 이상 연결이 없는 사용자의 엔트리는 제거한다.

## SSE 메시지 저장소

이벤트 유실 복원을 위해 SSE 메시지를 메모리에 저장하는 컴포넌트를 구현한다.

```java
@Repository
public class SseMessageRepository {

  private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
  private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();

  // ...
}
```

요구사항:

- 각 메시지마다 고유한 이벤트 ID를 부여한다.
- 이벤트 ID는 `UUID`를 사용한다.
- 메시지는 이벤트 ID, 이벤트 이름, 수신자 정보, 데이터 payload를 포함해야 한다.
- 클라이언트가 `Last-Event-ID`를 전송하면 해당 이벤트 이후의 메시지를 조회할 수 있어야 한다.
- 저장소는 스레드 세이프해야 한다.
- 메모리 사용량이 무한히 증가하지 않도록 오래된 메시지 정리 정책을 둔다.

## 이벤트 유실 복원

클라이언트는 SSE 재연결 시 마지막으로 수신한 이벤트 ID를 `Last-Event-ID`로 전송한다.

서버는 `Last-Event-ID` 기준으로 이후에 저장된 메시지를 조회하고, 해당 사용자에게 전달되어야 하는 메시지만 재전송한다.

요구사항:

- `Last-Event-ID`가 없으면 복원 없이 새 연결만 생성한다.
- `Last-Event-ID`가 저장소에 존재하면 이후 이벤트를 순서대로 재전송한다.
- `Last-Event-ID`가 저장소에 없으면 복원 가능한 이벤트가 없는 것으로 처리한다.
- 재전송 대상 이벤트는 현재 연결 사용자가 수신 대상인 이벤트로 제한한다.

## 폴링 리팩토링

기존에 클라이언트에서 주기적으로 요청하던 데이터를 SSE 이벤트 기반으로 변경한다.

요구사항:

- 새 알림 생성 이벤트를 SSE로 전송한다.
- 파일 업로드 상태 변경 이벤트를 SSE로 전송한다.
- 채널 생성, 수정, 삭제 이벤트를 SSE로 전송한다.
- 사용자 생성, 수정, 삭제 또는 로그인 상태 변경 이벤트를 SSE로 전송한다.
- 클라이언트는 이벤트를 수신하면 필요한 UI를 즉시 갱신한다.

## 이벤트 명세

### 새 알림 생성

새 알림이 생성되었을 때 클라이언트에 이벤트를 전송한다.

| 필드 | 값 |
| --- | --- |
| `id` | 이벤트 고유 ID |
| `name` | `notifications.created` |
| `data` | `NotificationDto` |

클라이언트 동작:

- 이벤트를 수신하면 알림 목록에 새 알림을 추가한다.

### 파일 업로드 상태 변경

파일 업로드 상태가 변경될 때 이벤트를 전송한다.

| 필드 | 값 |
| --- | --- |
| `id` | 이벤트 고유 ID |
| `name` | `binaryContents.updated` |
| `data` | `BinaryContentDto` |

클라이언트 동작:

- 이벤트를 수신하면 파일 상태 UI를 다시 렌더링한다.

### 채널 갱신

채널 정보가 변경될 때 이벤트를 전송한다.

| 필드 | 값 |
| --- | --- |
| `id` | 이벤트 고유 ID |
| `name` | `channels.created`, `channels.updated`, `channels.deleted` |
| `data` | `ChannelDto` |

클라이언트 동작:

- 이벤트를 수신하면 채널 UI를 다시 렌더링한다.

### 사용자 갱신

사용자 정보 또는 로그인 상태가 변경될 때 이벤트를 전송한다.

| 필드 | 값 |
| --- | --- |
| `id` | 이벤트 고유 ID |
| `name` | `users.created`, `users.updated`, `users.deleted` |
| `data` | `UserDto` |

클라이언트 동작:

- 이벤트를 수신하면 사용자 UI를 다시 렌더링한다.

## 수용 기준

- `GET /api/sse` 요청으로 SSE 연결을 생성할 수 있다.
- 사용자별로 여러 개의 SSE 연결을 동시에 유지할 수 있다.
- 서버에서 특정 사용자 또는 전체 사용자에게 SSE 이벤트를 전송할 수 있다.
- 클라이언트 재연결 시 `Last-Event-ID` 기준으로 유실된 이벤트를 복원할 수 있다.
- 만료되거나 전송 실패한 `SseEmitter` 연결은 저장소에서 제거된다.
- 알림, 파일 업로드 상태, 채널, 사용자 변경 사항이 폴링 없이 SSE 이벤트로 클라이언트에 전달된다.

## Docker Compose 기반 배포 아키텍처

Docker Compose를 이용해 프론트엔드 정적 리소스, 백엔드, 데이터베이스, Redis, Kafka를 하나의 내부 네트워크에서 실행한다.

외부에서 직접 접근 가능한 컨테이너는 Nginx Reverse Proxy 하나로 제한한다.

## 컨테이너 구성

### Nginx Reverse Proxy

Nginx는 외부 요청의 단일 진입점으로 동작한다.

요구사항:

- 사용자는 브라우저에서 `http://localhost:3000`으로 접근한다.
- Nginx 컨테이너만 외부 포트를 노출한다.
- Nginx는 `3000:80` 포트 매핑을 사용한다.
- `/api/*` 요청은 Spring Boot Backend 컨테이너로 프록시한다.
- `/ws/*` 요청은 Spring Boot Backend 컨테이너로 프록시한다.
- 그 외 요청은 Nginx 컨테이너 내부의 정적 리소스를 서빙한다.
- 프론트엔드 정적 리소스는 Nginx 컨테이너 내부의 `/usr/share/nginx/html` 등 적절한 경로에 위치해야 한다.

프록시 대상:

| 요청 경로 | 대상 |
| --- | --- |
| `/api/*` | `http://backend:8080` |
| `/ws/*` | `http://backend:8080` |
| 그 외 | Nginx 정적 리소스 |

### Backend

Backend는 Spring Boot 애플리케이션 컨테이너로 실행한다.

요구사항:

- Backend 컨테이너는 외부 포트를 노출하지 않는다.
- Nginx를 통해서만 `/api/*`, `/ws/*` 요청을 받는다.
- Docker Compose 내부 네트워크에서 PostgreSQL, Redis, Kafka에 접근한다.
- 내부 서비스 주소는 다음 값을 사용한다.

| 대상 | 내부 주소 |
| --- | --- |
| Backend | `backend:8080` |
| PostgreSQL | `db:5432` |
| Redis | `redis:6379` |
| Kafka | `broker:29092` |

### PostgreSQL

PostgreSQL은 Backend 전용 데이터베이스로 실행한다.

요구사항:

- 외부 포트를 노출하지 않는다.
- Backend는 `db:5432`로 PostgreSQL에 접근한다.
- 데이터 유지를 위해 Docker volume을 사용할 수 있다.

### Redis

Redis는 캐시 저장소로 실행한다.

요구사항:

- 외부 포트를 노출하지 않는다.
- Backend는 `redis:6379`로 Redis에 접근한다.
- 필요하면 Docker volume을 사용해 Redis 데이터를 유지할 수 있다.

### Kafka

Kafka는 이벤트 기반 알림 처리에 사용한다.

요구사항:

- 외부 포트를 노출하지 않는다.
- Backend는 `broker:29092`로 Kafka에 접근한다.
- Docker Compose 내부 네트워크에서 사용할 listener를 설정해야 한다.

## 외부 포트 노출 정책

외부 포트 노출은 Nginx로 제한한다.

| 서비스 | 외부 포트 노출 |
| --- | --- |
| Nginx | `3000:80` |
| Backend | 금지 |
| PostgreSQL | 금지 |
| Redis | 금지 |
| Kafka | 금지 |

## SSE 및 WebSocket 프록시 주의사항

### SSE

SSE 연결은 `/api/sse` 경로를 사용하므로 `/api/*` 프록시 대상에 포함된다.

요구사항:

- Nginx는 `/api/sse` 요청을 Backend의 `backend:8080`으로 프록시해야 한다.
- SSE는 장시간 유지되는 HTTP 연결이므로 프록시 타임아웃 설정을 고려해야 한다.
- 실시간 이벤트 전달을 위해 Nginx buffering 설정을 비활성화해야 할 수 있다.

### WebSocket

WebSocket 연결은 `/ws/*` 경로를 사용한다.

요구사항:

- Nginx는 `/ws/*` 요청을 Backend의 `backend:8080`으로 프록시해야 한다.
- WebSocket upgrade를 위해 `Upgrade`, `Connection` 헤더를 전달해야 한다.
- SockJS 하위 요청도 `/ws/*` 프록시 규칙으로 Backend에 전달되어야 한다.

## 배포 아키텍처 수용 기준

- 사용자는 `http://localhost:3000`으로 애플리케이션에 접근할 수 있다.
- Nginx만 외부 포트를 노출한다.
- `/api/*` 요청은 Nginx를 통해 Backend로 전달된다.
- `/ws/*` 요청은 Nginx를 통해 Backend로 전달된다.
- 그 외 요청은 Nginx가 정적 리소스로 응답한다.
- Backend는 Docker Compose 내부 네트워크에서 PostgreSQL, Redis, Kafka에 접근한다.
- PostgreSQL, Redis, Kafka, Backend는 외부에서 직접 접근할 수 없다.
