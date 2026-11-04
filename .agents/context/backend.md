# Backend: Domain Profile

Справочный контекст, не правила. Читать перед архитектурным анализом, изменением backend-кода и проектированием тестов. Нормативные требования — в `.agents/rules/`, ожидаемое поведение — в `openspec/specs/`.

Базовая точка аудита: commit `c537b69`, дата 2026-10-25. Факты проверены по коду, POM и конфигурации; намерения помечены отдельно. Стек, версии и тестовая инфраструктура перепроверены при актуализации `tech-debt-cleanup` (2026-11-04).

## Модули

| Модуль | Пакет | App | Роль | Хранилище |
|---|---|---|---|---|
| `common` | `io.averkhoglyad.shortcut.common` | — | `EntityResult`, ID callback, transaction callbacks, web handler, `KafkaHeaders`, `EventNameTypeResolver`, flow/log utils | — |
| `common-test` | `io.averkhoglyad.shortcut.common.test` | — | `SpringAwareKotestConfig`, `SqlTestCaseExtension`, matchers | — |
| `tx-outbox` | `io.averkhoglyad.shortcut.outbox` | — | доменно-нейтральное ядро outbox: `OutboxWriter`, `OutboxRelay`, `OutboxCleaner`, `OutboxDao` (`JdbcClient`), SPI `OutboxPublisher`, артефакты схемы `message_outbox` | — |
| `tx-outbox-starter` | `io.averkhoglyad.shortcut.outbox.autoconfigure` | — | автоконфигурация outbox: properties, роутер, провайдер заголовков, `OutboxEmitter`, дефолтный Kafka-publisher, scheduler, метрики | — |
| `users` | `io.averkhoglyad.shortcut.users` | `UsersApp` | REST `/users`, outbox publisher | PostgreSQL, outbox |
| `chats` | `io.averkhoglyad.shortcut.chat` | `ChatApp` | REST `/chats`, `/members`, outbox publisher | PostgreSQL, outbox |
| `messages` | `io.averkhoglyad.shortcut.message` | `MessageApp` | REST `/messages`, публикация после commit | PostgreSQL |
| `notifications` | `io.averkhoglyad.shortcut.notification` | `NotificationApp` | Kafka consumer, SSE, HTTP-клиент к `chats` | нет |
| `mail-sender` | `io.averkhoglyad.shortcut.mail.sender` | `MailSenderApp` | Kafka consumer, отправка почты | Redis |

`tx-outbox` и `tx-outbox-starter` — библиотечные модули, не сервисы, БД не владеют.

## Стек и версии

Версии — факты по корневому `pom.xml`, POM модулей и `.mvn/wrapper/maven-wrapper.properties`; нормативные требования к стеку — `openspec/specs/platform/stack/spec.md`.

- Java 17, Kotlin 2.4.21 (`kotlin.version` переопределяет управляемую Boot версию), Spring Boot 4.1.2 (BOM `spring-boot-dependencies`): Spring Framework 7, Spring Kafka 4.1.2, Jackson 3 (managed 3.1.7).
- Jackson 3: координаты `tools.jackson.*`, аннотации остались `com.fasterxml.jackson.annotation`; Jackson 2 (databind/core) в коде отсутствует.
- Тесты: Kotest 6.2.6 (`io.kotest:*`), MockK 1.14.11 (`mockk-jvm`), Testcontainers 2.0.5 (BOM). SpringMockK удалён: в коде не использовался, версии под Boot 4 нет.
- Прочее: ShedLock 7.10.1, `java-uuid-generator` 5.2.0, `hypersistence-tsid` 2.1.4, Jakarta Mail API 2.1.5, `loki-logback-appender` 2.0.3 (2.1.0 требует Logback 1.6, Boot 4.1.2 управляет 1.5.38).
- Сборка: Maven 3.9.16 через Maven Wrapper 3.3.4; `maven-surefire-plugin` 3.5.6 закреплён в `pluginManagement` (версия Boot 4.1.2-managed; дефолт Maven 3.9.16 — 3.5.4) для стабильного репортинга Kotest 6 / JUnit Platform 6.

## Тестовая инфраструктура

- `common-test`: `SpringAwareKotestConfig` регистрирует единственное расширение `SpringExtension()`; `SpringAutowireConstructorExtension` в Kotest 6 не используется. Также `SqlTestCaseExtension`/`sql(...)` и matchers.
- Конфигурация Kotest берётся из `kotest.properties` в `src/test/resources`; такой файл есть во всех восьми модулях с тестами, включая `common`, `tx-outbox` и `tx-outbox-starter`.
- Testcontainers 2: координаты `testcontainers-postgresql`, `testcontainers-kafka`, `testcontainers-junit-jupiter`; пакеты `org.testcontainers.postgresql.PostgreSQLContainer`, `org.testcontainers.kafka.ConfluentKafkaContainer`; мосты `org.testcontainers.containers.*` не используются. `TestcontainersConfiguration` с `@ServiceConnection` есть только в `users` (PostgreSQL), `chats` и `messages` (PostgreSQL и Kafka); `tx-outbox` создаёт `PostgreSQLContainer` напрямую в `OutboxDaoIntegrationTest`, `notifications` объявляет `testcontainers-postgresql`, но контейнер в тестах не использует.
- Boot 4: test-аннотации в новых пакетах — `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`, `org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest`, `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`.
- `kotest-extensions-testcontainers` объявлен в `users` и `chats`, в тестах не используется.

## Точки входа

- REST: `UserController` `/users`; `ChatController` `/chats`; `ChatMembersController` `/members`; `MessageController` `/messages`.
- Kafka: `SendCommandHandler` (`MailerCommands`); `ChatLifecycleEventsHandler` (`ChatLifecycle`, тип события по `X-Event-Name`).
- SSE: `GET /subscribe?userId=...` — `WebConfig` и `NotificationSubscriptionHandler`.
- Outbox jobs: `tx-outbox-starter` (`TxOutboxScheduleConfig`, relay и cleanup на `outboxTaskScheduler`, профиль `!test`); сервисы дают только `LockProvider` в своём `ScheduleConfig`.
- Тесты: `<module>/src/test/kotlin`; фикстуры `src/test/resources/it/**` и `src/test/resources/repository/**` в `users`, `chats`, `messages`; `repository/**` также в `notifications`; генераторы `gen.kt` в `chats`, `messages`, `mail-sender`, `tx-outbox`, `tx-outbox-starter`.

## Взаимодействия

- Web: `users`, `chats`, `messages` — Spring MVC; `notifications` — WebFlux.
- `X-User-Id` — текущий dev-механизм идентификации; у SSE идентификатор передаётся в query `userId`.
- Kafka producers:
  - `users`: `MailerCommands` (тип outbox `SendCreatedUserNotification`, имя события `SendEmail`) и `UserLifecycle` (`UserCreated`, `UserUpdated`, `UserDeleted`) через outbox;
  - `chats`: `ChatLifecycle` (`ChatCreated`) через outbox;
  - `messages`: `ChatLifecycle` (`MessagePublished`) напрямую после commit.
- Kafka consumers: `mail-sender` (`MailerCommands`), `notifications` (`ChatLifecycle`), `chats` и `messages` (`UserLifecycle`).
- Kafka-сериализация: JSON через Jackson 3 и Spring Kafka 4 — `JacksonJsonDeserializer` с `JacksonJsonTypeResolver` (`common.kafka.EventNameTypeResolver`, тип по `X-Event-Name`, `setUseTypeHeaders(false)`) в `chats`, `messages`, `notifications`; `messages` публикует через `JacksonJsonSerializer`, `users`/`chats` публикуют JSON-байты из outbox (`ByteArraySerializer`); `mail-sender` потребляет `JacksonJsonDeserializer` с `spring.json.use.type.headers=false`.
- HTTP: `notifications` вызывает `chats` через `GET /members?chatId=...`; адрес — `integrations.chat-service.base-url`.
- Redis: `mail-sender` хранит состояние идемпотентности по `X-Event-Id`, TTL 30 дней.
- SSE: `notifications` отдаёт live-поток из `MutableSharedFlow` без replay. Модель live-only; требование к replay не зафиксировано, решение — отдельно.
- Outbox: `users`/`chats` пишут событие в таблицу в транзакции агрегата через `OutboxEmitter`; `tx-outbox-starter` публикует его через claim/lease с подтверждением Kafka, повторяет до `DEAD` с backoff, восстанавливает просроченные lease и чистит терминальные записи по retention. Доставка at-least-once, consumer'ы должны быть идемпотентны.

## Текущее состояние и ограничения

Это факты текущего поведения, не требования.

Подтверждено кодом и конфигурацией:

- авторизации нет;
- `chats` и `messages` потребляют `UserLifecycle` и ведут локальную проекцию `users` с идемпотентностью по `last_sync_at`; `users` публикует `UserUpdated`/`UserDeleted` через outbox;
- `messages` осознанно публикует `MessagePublished` напрямую после commit, минуя outbox, ради низкой задержки интерактивной отправки; доставка at-most-once, потеря события после commit допустима. Решение зафиксировано в `system/runtime-guarantees`; для `users`/`chats` outbox даёт at-least-once, возможны дубли, consumer'ы должны быть идемпотентны;
- `chats` публикует единственное chat-событие `ChatCreated` через outbox на `ChatLifecycle`. Mutation-ручек (`PUT/DELETE /chats`, управление участниками) нет, поэтому нет `ChatUpdated`, `ChatDeleted` и member-событий. `messages`/`MessagePublished` — message-событие, в chat-скоуп не входит;
- `mail-sender` при ошибке сохраняет `FAILED` и не пробрасывает исключение; retry/DLQ нет. Требования к retry и обработке ошибок не зафиксированы, решение — отдельно;
- `notifications` не имеет собственной БД; в POM остались Liquibase/PostgreSQL, в `src/test/resources` — SQL-фикстуры. Проверка этих зависимостей и фикстур — отдельная задача;
- `common` объявляет `test-compile` и содержит тесты в `src/test/kotlin` (`FlowTest`, `SynchronizationTest`, `EntityResultHandlerTest`); предположение базового аудита о дефекте тестовой инфраструктуры `common` не подтверждено;
- Datasource URL задаётся извне; дефолтного URL в `application.yaml` нет. Контуры `users` передают URL явно; контуров для `chats`/`messages` в репозитории нет;
- CI, линтеров, форматтеров и integration-test профиля нет;
- в `docker/docker-compose.yml`, Helm и K8s развёрнуты только `users` и `mail-sender`;
- `PageResponse` не используется; `hypersistence-tsid` объявлен в `dependencyManagement`, но в коде не используется;
- outbox и Kafka-заголовки вынесены в `tx-outbox`/`tx-outbox-starter`; `EndpointExceptionAdvice` и response-классы дублируются между модулями;
- Spring Security, JPA/Hibernate, MapStruct, OpenAPI и schema registry не подключены.

## Ссылки на источники

- Границы модулей, владение БД, Kafka, outbox: `.agents/rules/architecture/module-boundaries.md`.
- REST/Kafka-контракты: `.agents/rules/architecture/contracts.md`.
- Соглашения разработки: `.agents/rules/development/conventions.md`.
- Стек и test infrastructure: `openspec/specs/platform/stack/spec.md`.
- Стиль тестов: `.agents/rules/testing/test-style.md`.
- Проверка изменений: `.agents/procedures/verification.md`.
- Определения системы: `openspec/specs/`.
- Модули, версии, зависимости: корневой `pom.xml`, `<module>/pom.xml`.
- Контуры развёртывания: `docker/docker-compose.yml`, `helm/`, `k8s/`.
- Тесты и фикстуры: `src/test` соответствующего модуля.
