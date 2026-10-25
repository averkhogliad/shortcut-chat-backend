# Backend: справочный контекст

Справочные сведения, не обязательные правила. Состояние на 2026-10-25.

## Модули

- `common` — `EntityResult`, транзакционные callbacks, генерация ID, web-обработчики, flow-утилиты, логирование.
- `common-test` — Kotest Spring config, SQL extension, matchers.
- `users` — REST `/users`, PostgreSQL, outbox, Kafka producer.
- `chats` — REST `/chats`, `/members`, PostgreSQL, outbox, Kafka producer.
- `messages` — REST `/messages`, PostgreSQL, прямой Kafka producer после commit.
- `notifications` — Kafka consumer `ChatLifecycle`, SSE `/subscribe`, HTTP-клиент к `chats`; собственной БД нет.
- `mail-sender` — Kafka consumer `MailerCommands`, Redis-идемпотентность, отправка почты.
- `tx-outbox/` — пустая заготовка будущей библиотеки, в Maven-модули не входит.

## Версии

- Java 17, Kotlin 1.9.25, Spring Boot 3.4.0, Maven Wrapper 3.9.9.
- ShedLock 6.0.2, `java-uuid-generator` 5.1.0, Jakarta Mail API 2.1.3.
- Kotest 5.9.1, MockK 1.13.16, SpringMockK 4.0.2.
- PostgreSQL 14, Kafka/Confluent 7.8.0, Redis 7.4.2.
- Loki 3.5.3, Grafana 12.1.0.

## Текущее устройство

- `users`, `chats`, `messages` — Spring MVC, Spring Data JDBC, Liquibase, PostgreSQL, Kafka, Actuator, Prometheus, Brave.
- `notifications` — WebFlux, coroutines, Kafka consumer, SSE.
- `mail-sender` — Spring MVC, Kafka, Spring Mail, Spring Data Redis.
- Datasource URL задаётся извне; дефолтного URL в `application.yaml` больше нет. Контуры `users` передают URL явно, контуров для `chats`/`messages` в репозитории нет.
- Kafka-потоки: `users -> MailerCommands` (`SendCreatedUserNotification`), `users -> UserLifecycle` (`UserCreated`), `chats -> ChatLifecycle` (`ChatCreated`), `messages -> ChatLifecycle` (`MessagePublished`), `notifications <- ChatLifecycle`, `mail-sender <- MailerCommands`.
- REST-контракт пагинации: `SliceResponse { data, next }`.
- Ошибки: `EntityResult` в сервисах, `ProblemDetail` в HTTP.

## Технический долг и ограничения

- Авторизации нет. `X-User-Id` и `userId` в query — доверенные dev-механизмы.
- `UserLifecycle` не имеет consumer'а. Синхронизация read-моделей `users`/`chats` в `chats`/`messages` не реализована.
- Outbox, Kafka-заголовки, `EndpointExceptionAdvice` и response-классы дублируются между модулями. `tx-outbox` для выноса outbox не реализован.
- Outbox handler отправляет событие и сразу помечает запись опубликованной, не ожидая подтверждения Kafka.
- `messages` публикует событие после commit без outbox; событие может потеряться.
- `mail-sender` при ошибке сохраняет `FAILED` и не пробрасывает исключение; retry/DLQ нет.
- `notifications` хранит события в `MutableSharedFlow` без replay: SSE live-only, без активного клиента события теряются.
- В `notifications` остаются Liquibase/PostgreSQL-зависимости и SQL-ресурсы, хотя собственной БД нет.
- `common` не компилирует тесты; `FlowTest` не запускается.
- Нет integration-test профиля, CI, линтеров и форматтеров.
- В `docker/docker-compose.yml`, Helm и K8s присутствуют только `users` и `mail-sender`; `chats`, `messages`, `notifications` не развёрнуты.
- Spring Security, JPA/Hibernate, MapStruct, OpenAPI и schema registry не подключены.
