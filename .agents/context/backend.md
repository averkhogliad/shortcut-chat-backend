# Backend: Domain Profile

Справочный контекст, не правила. Читать перед архитектурным анализом, изменением backend-кода и проектированием тестов. Нормативные требования — в `.agents/rules/`, ожидаемое поведение — в `openspec/specs/`.

Базовая точка аудита: commit `c537b69`, дата 2026-10-25. Факты проверены по коду, POM и конфигурации; намерения помечены отдельно.

## Модули

| Модуль | Пакет | App | Роль | Хранилище |
|---|---|---|---|---|
| `common` | `io.averkhoglyad.shortcut.common` | — | `EntityResult`, ID callback, transaction callbacks, web handler, flow/log utils | — |
| `common-test` | `io.averkhoglyad.shortcut.common.test` | — | `SpringAwareKotestConfig`, `SqlTestCaseExtension`, matchers | — |
| `users` | `io.averkhoglyad.shortcut.users` | `UsersApp` | REST `/users`, outbox publisher | PostgreSQL, outbox |
| `chats` | `io.averkhoglyad.shortcut.chat` | `ChatApp` | REST `/chats`, `/members`, outbox publisher | PostgreSQL, outbox |
| `messages` | `io.averkhoglyad.shortcut.message` | `MessageApp` | REST `/messages`, публикация после commit | PostgreSQL |
| `notifications` | `io.averkhoglyad.shortcut.notification` | `NotificationApp` | Kafka consumer, SSE, HTTP-клиент к `chats` | нет |
| `mail-sender` | `io.averkhoglyad.shortcut.mail.sender` | `MailSenderApp` | Kafka consumer, отправка почты | Redis |

`tx-outbox/` — пустой каталог, в Maven-модули не входит.

## Точки входа

- REST: `UserController` `/users`; `ChatController` `/chats`; `ChatMembersController` `/members`; `MessageController` `/messages`.
- Kafka: `SendCommandHandler` (`MailerCommands`); `ChatLifecycleEventsHandler` (`ChatLifecycle`, тип события по `X-Event-Name`).
- SSE: `GET /subscribe?userId=...` — `WebConfig` и `NotificationSubscriptionHandler`.
- Outbox jobs: `ScheduleConfig` в `users` и `chats` (`message-outbox-handler`, `message-outbox-cleaner`, профиль `!test`), `OutboxHandler`, `OutboxCleaner`.
- Тесты: `<module>/src/test/kotlin`; фикстуры `src/test/resources/it/**` и `src/test/resources/repository/**` в `chats`, `messages`, `notifications`; генераторы `gen.kt` в `users`, `chats`, `messages`, `mail-sender`.

## Взаимодействия

- Web: `users`, `chats`, `messages` — Spring MVC; `notifications` — WebFlux.
- `X-User-Id` — текущий dev-механизм идентификации; у SSE идентификатор передаётся в query `userId`.
- Kafka producers:
  - `users`: `MailerCommands` (тип outbox `SendCreatedUserNotification`, имя события `SendEmail`) и `UserLifecycle` (`UserCreated`) через outbox;
  - `chats`: `ChatLifecycle` (`ChatCreated`) через outbox;
  - `messages`: `ChatLifecycle` (`MessagePublished`) напрямую после commit.
- Kafka consumers: `mail-sender` (`MailerCommands`), `notifications` (`ChatLifecycle`).
- HTTP: `notifications` вызывает `chats` через `GET /members?chatId=...`; адрес — `integrations.chat-service.base-url`.
- Redis: `mail-sender` хранит состояние идемпотентности по `X-Event-Id`, TTL 30 дней.
- SSE: `notifications` отдаёт live-поток из `MutableSharedFlow` без replay. Модель live-only; требование к replay не зафиксировано, решение — отдельно.
- Outbox: `users`/`chats` пишут событие в таблицу в транзакции агрегата; scheduler отправляет его и сразу помечает published; подтверждения Kafka handler не ждёт. Это известный риск; решение о гарантиях публикации — отдельно.

## Текущее состояние и ограничения

Это факты текущего поведения, не требования.

Подтверждено кодом и конфигурацией:

- авторизации нет;
- consumer `UserLifecycle` отсутствует; consumer'ов в `chats`/`messages` нет; синхронизация read-моделей не реализована, критерии приёмки не определены;
- `messages` может потерять событие после commit; outbox не гарантирует подтверждённую доставку. Это известный риск, решение о гарантиях публикации — отдельно;
- `mail-sender` при ошибке сохраняет `FAILED` и не пробрасывает исключение; retry/DLQ нет. Требования к retry и обработке ошибок не зафиксированы, решение — отдельно;
- `notifications` не имеет собственной БД; в POM остались Liquibase/PostgreSQL, в `src/test/resources` — SQL-фикстуры. Проверка этих зависимостей и фикстур — отдельная задача;
- `common` POM не объявляет `test-compile`; `FlowTest` — предполагаемый дефект тестовой инфраструктуры по статическому аудиту; сборка не запускалась; рабочим покрытием не является;
- Datasource URL задаётся извне; дефолтного URL в `application.yaml` нет. Контуры `users` передают URL явно; контуров для `chats`/`messages` в репозитории нет;
- CI, линтеров, форматтеров и integration-test профиля нет;
- в `docker/docker-compose.yml`, Helm и K8s развёрнуты только `users` и `mail-sender`;
- `PageResponse` не используется; `hypersistence-tsid` объявлен в `dependencyManagement`, но в коде не используется;
- outbox, Kafka-заголовки, `EndpointExceptionAdvice` и response-классы дублируются между модулями;
- Spring Security, JPA/Hibernate, MapStruct, OpenAPI и schema registry не подключены.

Намерения, не реализованные в коде:

- синхронизация read-моделей `users`/`chats` (заявлена в `module-boundaries.md` как будущая задача; критерии приёмки не определены);
- вынос outbox в `tx-outbox` (зафиксирован в TODO и пустом каталоге).

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
