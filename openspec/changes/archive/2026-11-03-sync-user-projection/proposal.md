# Proposal

## Why

`users` публикует `UserCreated` в топик `UserLifecycle`, но событие не потребляется ни одним сервисом. `chats` и `messages` владеют локальными проекциями `users` (с FK `chat_members.user_id`, `chats.owner_id`, `messages.author_id`), которые сейчас наполняются только тестовыми фикстурами. Создание чата с реальными user id и публикация сообщения падают на FK, а `ChatCreated.members` строится по неполной локальной таблице. Кроме того, `PUT /users/{id}` не порождает события вообще, а удаления пользователя не моделировано. Без проекции пользователи `chats` и `messages` не могут работать с реальными данными.

## What Changes

- Добавить событие `UserUpdated` (payload `User { id, name, email }`) в топик `UserLifecycle`, публикуемое `users` через outbox в той же транзакции, что и изменение агрегата.
- Добавить событие `UserDeleted` (payload `UserDeleted { id }`) в топик `UserLifecycle`, публикуемое `users` через outbox. Удаление — отдельный event от обновления; мягкое удаление (`is_deleted`) вне scope.
- Реализовать в `chats` consumer топика `UserLifecycle`, обрабатывающий `UserCreated`, `UserUpdated`, `UserDeleted` и актуализирующий локальную таблицу `users` (upsert / soft-mark / удаление строки).
- Реализовать в `messages` consumer топика `UserLifecycle`, обрабатывающий те же три события и актуализирующий локальную таблицу `users`.
- Идемпотентность и упорядоченность consumer'ов: сравнение по `user.id` и времени события — событие старше `users.last_sync_at` игнорируется; `last_sync_at` обновляется на применённое событие.
- **BREAKING**: `UserLifecycle` получает consumer'ы; ранее spec фиксировал consumer `none`. Контракт payload `UserCreated` и заголовки не меняются (аддитивное изменение — добавляются `UserUpdated`, `UserDeleted`).

## Capabilities

### New Capabilities

_Нет новых capability — изменения затрагивают существующие спецификации системы._

### Modified Capabilities

- `system/kafka-messaging`: добавляются события `UserUpdated`, `UserUpdated` и `UserDeleted` в inventory топика `UserLifecycle`; добавляются consumer'ы `chats` и `messages`; требование идемпотентной актуализации проекции по `id` и времени события.
- `system/architecture`: `chats` и `messages` становятся consumer'ами `UserLifecycle` для синхронизации read-модели `users`; снимается оговорка «consumer `UserLifecycle` отсутствует / синхронизация не реализована» в части пользователей.
- `system/runtime-guarantees`: фиксируется гарантия упорядоченной актуализации проекции (ignore устаревших событий по `last_sync_at`) и доставка user-lifecycle через outbox at-least-once.

## Impact

- Код:
  - `users`: `UserService.update` — публикация `UserUpdated` через outbox; новая операция удаления пользователя + `UserDeletedMessageFactory`; `application.yaml` — маршруты outbox `UserUpdated`, `UserDeleted` в канал `user-lifecycle`.
  - `chats`: новый `UserLifecycleEventsHandler` (или расширение существующего Kafka-конфига), `UserProjectionService`, репозиторий `users` upsert; `KafkaConfig` — consumer factory для `UserLifecycle`.
  - `messages`: то же — consumer `UserLifecycle`, `UserProjectionService`, upsert в `users`.
- API: `DELETE /users/{id}` (новый endpoint) для запуска `UserDeleted`. `PUT /users/{id}` начинает порождать событие (поведение).
- БД: существующие таблицы `users` в `chats`/`messages` уже имеют `last_sync_at`, `is_deleted` — миграций не требуется; при необходимости индексы.
- Зависимости: `chats` и `messages` получают Kafka consumer (spring-kafka уже в stack); новый общий код выноса consumer-инфраструктуры — по согласованию (возможно, в `common`).
- Specs: `system/kafka-messaging`, `system/architecture`, `system/runtime-guarantees`.
- Риски: дубли из outbox (at-least-once) — обработка идемпотентна по `id`+`last_sync_at`; нарушение порядка событий между `UserCreated`/`UserUpdated`/`UserDeleted` — решается сравнением времени события с `last_sync_at`.